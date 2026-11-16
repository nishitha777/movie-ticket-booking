package local.basilbyte;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/** Small JDBC backend for the Tkinter movie ticket screen. */
public class MovieTicketServer {
    private static final ObjectMapper json = new ObjectMapper();
    private static final String url = System.getenv().getOrDefault(
            "DB_URL", "jdbc:mysql://localhost:3307/movie_ticket_booking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String user = System.getenv().getOrDefault("DB_USER", "restaurant");
    private static final String password = System.getenv().getOrDefault("DB_PASSWORD", "");

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        createTables();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/api/movies", MovieTicketServer::showMovies);
        server.createContext("/api/seats", MovieTicketServer::showSeats);
        server.createContext("/api/bookings", MovieTicketServer::bookSeats);
        server.start();
        System.out.println("Connected to MySQL. Movie ticket server is ready on port 8080.");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void createTables() throws SQLException {
        try (Connection db = connect(); Statement sql = db.createStatement()) {
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS movies (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, price DECIMAL(8,2) NOT NULL)");
            sql.executeUpdate("CREATE TABLE IF NOT EXISTS bookings (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, customer_name VARCHAR(100) NOT NULL, " +
                    "movie_id INT NOT NULL, show_time VARCHAR(20) NOT NULL, seat VARCHAR(5) NOT NULL, " +
                    "price DECIMAL(8,2) NOT NULL, booked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(movie_id, show_time, seat), FOREIGN KEY (movie_id) REFERENCES movies(id))");

            ResultSet count = sql.executeQuery("SELECT COUNT(*) FROM movies");
            count.next();
            if (count.getInt(1) == 0) {
                sql.executeUpdate("INSERT INTO movies(name, price) VALUES " +
                        "('Space Rangers', 180), ('Ocean Mystery', 150), ('Funny Neighbours', 120)");
            }
        }
    }

    private static void showMovies(HttpExchange request) throws IOException {
        try (Connection db = connect();
             PreparedStatement sql = db.prepareStatement("SELECT id, name, price FROM movies ORDER BY id");
             ResultSet rows = sql.executeQuery()) {
            ArrayNode movies = json.createArrayNode();
            while (rows.next()) {
                ObjectNode movie = movies.addObject();
                movie.put("id", rows.getInt("id"));
                movie.put("name", rows.getString("name"));
                movie.put("price", rows.getBigDecimal("price"));
            }
            send(request, 200, movies);
        } catch (SQLException error) {
            sendError(request, error);
        }
    }

    private static void showSeats(HttpExchange request) throws IOException {
        Map<String, String> query = readQuery(request);
        ArrayNode seats = json.createArrayNode();
        String sqlText = "SELECT seat FROM bookings WHERE movie_id=? AND show_time=?";
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(sqlText)) {
            sql.setInt(1, Integer.parseInt(query.getOrDefault("movieId", "0")));
            sql.setString(2, query.getOrDefault("showTime", ""));
            ResultSet rows = sql.executeQuery();
            while (rows.next()) seats.add(rows.getString("seat"));
            send(request, 200, seats);
        } catch (Exception error) {
            sendError(request, error);
        }
    }

    private static void bookSeats(HttpExchange request) throws IOException {
        try {
            JsonNode data = json.readTree(request.getRequestBody());
            String name = data.path("customerName").asText().trim();
            String show = data.path("showTime").asText();
            int movieId = data.path("movieId").asInt();
            JsonNode seats = data.path("seats");
            if (name.isEmpty() || show.isEmpty() || movieId < 1 || !seats.isArray() || seats.isEmpty()) {
                send(request, 400, error("Enter your name and select at least one seat."));
                return;
            }

            try (Connection db = connect()) {
                db.setAutoCommit(false);
                BigDecimal price;
                try (PreparedStatement movie = db.prepareStatement("SELECT price FROM movies WHERE id=?")) {
                    movie.setInt(1, movieId);
                    ResultSet row = movie.executeQuery();
                    if (!row.next()) throw new IllegalArgumentException("Movie not found.");
                    price = row.getBigDecimal("price");
                }

                String addBooking = "INSERT INTO bookings(customer_name, movie_id, show_time, seat, price) " +
                        "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement booking = db.prepareStatement(addBooking)) {
                    for (JsonNode seat : seats) {
                        String seatName = seat.asText();
                        if (!seatName.matches("[A-F][1-8]")) throw new IllegalArgumentException("Invalid seat.");
                        booking.setString(1, name);
                        booking.setInt(2, movieId);
                        booking.setString(3, show);
                        booking.setString(4, seatName);
                        booking.setBigDecimal(5, price);
                        booking.addBatch();
                    }
                    booking.executeBatch();
                }
                db.commit();

                ObjectNode result = json.createObjectNode();
                result.put("bookingId", "saved");
                result.put("total", price.multiply(BigDecimal.valueOf(seats.size())));
                send(request, 201, result);
            }
        } catch (IllegalArgumentException error) {
            send(request, 400, error(error.getMessage()));
        } catch (SQLException error) {
            send(request, 409, error("One of those seats was just booked. Please choose again."));
        } catch (Exception error) {
            send(request, 500, error(error.getMessage()));
        }
    }

    private static Map<String, String> readQuery(HttpExchange request) {
        Map<String, String> values = new HashMap<>();
        String query = request.getRequestURI().getRawQuery();
        if (query != null) for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2) values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private static ObjectNode error(String message) {
        ObjectNode result = json.createObjectNode();
        result.put("error", message);
        return result;
    }

    private static void sendError(HttpExchange request, Exception error) throws IOException {
        send(request, 500, error(error.getMessage()));
    }

    private static void send(HttpExchange request, int status, JsonNode data) throws IOException {
        byte[] body = json.writeValueAsBytes(data);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(status, body.length);
        request.getResponseBody().write(body);
        request.close();
    }
}
