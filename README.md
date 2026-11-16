# Movie Ticket Booking

Tkinter GUI with a Java JDBC backend and MySQL database.

1. Run `database.sql` once in MySQL Workbench.
2. In PowerShell, from the project folder, set your MySQL login and start the backend:

   ```powershell
   $env:DB_USER = "restaurant"
   $env:DB_PASSWORD = "your MySQL password"
   cd backend
   javac -cp "lib\mysql-connector-j-9.5.0.jar" -d out src\main\java\local\basilbyte\MovieTicketServer.java
   java -cp "out;lib\mysql-connector-j-9.5.0.jar" local.basilbyte.MovieTicketServer
   ```

3. In another terminal from the project folder, run `python movie_booking.py`.

The backend needs only the MySQL Connector/J JAR in `backend/lib`. It defaults to MySQL at `localhost:3307`, database `movie_ticket_booking`. Set `DB_URL`, `DB_USER`, and `DB_PASSWORD` if your connection settings differ.
