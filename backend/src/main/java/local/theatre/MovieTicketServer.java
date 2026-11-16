package local.theatre;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small JDBC backend for the Tkinter movie ticket screen. */
public class MovieTicketServer {
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
            StringBuilder movies = new StringBuilder("[");
            while (rows.next()) {
                if (movies.length() > 1) movies.append(',');
                movies.append("{\"id\":").append(rows.getInt("id"))
                        .append(",\"name\":").append(jsonString(rows.getString("name")))
                        .append(",\"price\":").append(rows.getBigDecimal("price")).append('}');
            }
            send(request, 200, movies.append(']').toString());
        } catch (SQLException error) {
            sendError(request, error);
        }
    }

    private static void showSeats(HttpExchange request) throws IOException {
        Map<String, String> query = readQuery(request);
        StringBuilder seats = new StringBuilder("[");
        String sqlText = "SELECT seat FROM bookings WHERE movie_id=? AND show_time=?";
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(sqlText)) {
            sql.setInt(1, Integer.parseInt(query.getOrDefault("movieId", "0")));
            sql.setString(2, query.getOrDefault("showTime", ""));
            ResultSet rows = sql.executeQuery();
            while (rows.next()) {
                if (seats.length() > 1) seats.append(',');
                seats.append(jsonString(rows.getString("seat")));
            }
            send(request, 200, seats.append(']').toString());
        } catch (Exception error) {
            sendError(request, error);
        }
    }

    private static void bookSeats(HttpExchange request) throws IOException {
        try {
            String data = new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String name = jsonValue(data, "customerName").trim();
            String show = jsonValue(data, "showTime");
            int movieId = Integer.parseInt(numberValue(data, "movieId"));
            List<String> seats = seatValues(data);
            if (name.isEmpty() || show.isEmpty() || movieId < 1 || seats.isEmpty()) {
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
                    for (String seatName : seats) {
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

                String result = "{\"bookingId\":\"saved\",\"total\":"
                        + price.multiply(BigDecimal.valueOf(seats.size())) + "}";
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

    private static String error(String message) {
        return "{\"error\":" + jsonString(message) + "}";
    }

    private static void sendError(HttpExchange request, Exception error) throws IOException {
        send(request, 500, error(error.getMessage()));
    }

    private static void send(HttpExchange request, int status, String data) throws IOException {
        byte[] body = data.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(status, body.length);
        request.getResponseBody().write(body);
        request.close();
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }

    private static String jsonValue(String json, String key) {
        Matcher match = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);
        if (!match.find()) return "";
        String value = match.group(1);
        return value.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private static String numberValue(String json, String key) {
        Matcher match = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!match.find()) return "0";
        return match.group(1);
    }

    private static List<String> seatValues(String json) {
        List<String> seats = new ArrayList<>();
        Matcher array = Pattern.compile("\\\"seats\\\"\\s*:\\s*\\[([^]]*)\\]").matcher(json);
        if (!array.find()) return seats;
        Matcher seat = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(array.group(1));
        while (seat.find()) seats.add(seat.group(1));
        return seats;
    }
}
