# Movie Ticket Booking

Simple Tkinter GUI with a Java JDBC backend and MySQL database.

1. In MySQL Workbench, run `database.sql` once.
2. In PowerShell, set your MySQL login, then start the Java backend:

   ```powershell
   $env:DB_USER = "restaurant"
   $env:DB_PASSWORD = "your MySQL password"
   .\backend\run_backend.bat
   ```

3. In another terminal in this folder, run `python movie_booking.py`.

The MySQL connection defaults to `localhost:3307` and database `movie_ticket_booking`. Set `DB_URL`, `DB_USER`, and `DB_PASSWORD` environment variables if your MySQL settings differ. The password is not stored in this project. Required Java libraries are included in `backend/lib`.
