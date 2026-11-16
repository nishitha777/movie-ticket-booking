package local.theatre;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MovieTicketServer {
    private static final String URL = System.getenv().getOrDefault(
            "DB_URL", "jdbc:mysql://localhost:3307/movie_ticket_booking");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "restaurant");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/api/seats", MovieTicketServer::showSeats);
        server.createContext("/api/bookings", MovieTicketServer::bookSeats);
        server.start();
        System.out.println("Connected to MySQL. Server is running on port 8080.");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static void showSeats(HttpExchange request) throws IOException {
        Map<String, String> data = values(request.getRequestURI().getRawQuery());
        String sqlText = "SELECT seat FROM bookings WHERE movie_id=? AND show_time=?";
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(sqlText)) {
            sql.setInt(1, Integer.parseInt(data.getOrDefault("movieId", "0")));
            sql.setString(2, data.getOrDefault("showTime", ""));
            ResultSet rows = sql.executeQuery();
            StringBuilder seats = new StringBuilder();
            while (rows.next()) {
                if (seats.length() > 0) seats.append(',');
                seats.append(rows.getString("seat"));
            }
            send(request, 200, seats.toString());
        } catch (Exception error) {
            send(request, 500, "Could not read booked seats.");
        }
    }

    private static void bookSeats(HttpExchange request) throws IOException {
        Map<String, String> data = values(new String(
                request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String name = data.getOrDefault("name", "").trim();
        String show = data.getOrDefault("showTime", "");
        String[] seats = data.getOrDefault("seats", "").split(",");
        try {
            int movieId = Integer.parseInt(data.getOrDefault("movieId", "0"));
            if (name.isEmpty() || show.isEmpty() || movieId < 1 || seats[0].isEmpty()) {
                send(request, 400, "Enter your name and select seats.");
                return;
            }

            try (Connection db = connect()) {
                db.setAutoCommit(false);
                String add = "INSERT INTO bookings(customer_name,movie_id,show_time,seat,price) " +
                        "VALUES(?,?,?, ?, (SELECT price FROM movies WHERE id=?))";
                try (PreparedStatement sql = db.prepareStatement(add)) {
                    for (String seat : seats) {
                        if (!seat.matches("[A-F][1-8]")) throw new IllegalArgumentException();
                        sql.setString(1, name);
                        sql.setInt(2, movieId);
                        sql.setString(3, show);
                        sql.setString(4, seat);
                        sql.setInt(5, movieId);
                        sql.addBatch();
                    }
                    sql.executeBatch();
                }
                db.commit();
                send(request, 201, "Booked");
            }
        } catch (IllegalArgumentException error) {
            send(request, 400, "Enter valid booking details and seats.");
        } catch (SQLException error) {
            send(request, 409, "Booking failed. A seat may already be taken.");
        }
    }

    private static Map<String, String> values(String text) {
        Map<String, String> data = new HashMap<>();
        if (text != null) for (String pair : text.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) data.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return data;
    }

    private static void send(HttpExchange request, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        request.sendResponseHeaders(status, body.length);
        request.getResponseBody().write(body);
        request.close();
    }
}
