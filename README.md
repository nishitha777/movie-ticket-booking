# Movie Ticket Booking

Simple Tkinter GUI with a Java JDBC backend and MySQL database.

1. In MySQL Workbench, run `database.sql` once.
2. In PowerShell, set your MySQL login, then start the backend:

   ```powershell
   $env:DB_USER = "restaurant"
   $env:DB_PASSWORD = "your MySQL password"
   cd backend
   mvn exec:java
   ```

3. In another terminal in this folder, run `python movie_booking.py`.

The MySQL connection defaults to `localhost:3307` and database `movie_ticket_booking`. Set `DB_URL`, `DB_USER`, and `DB_PASSWORD` environment variables if your MySQL settings differ. The password is not stored in this project.
