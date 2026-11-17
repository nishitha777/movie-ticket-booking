# 🎬 Movie Ticket Booking System

A cinema-style seat booking desktop app. Pick a movie and show time, click seats on a live seat map, and book. Bookings are stored in a database, so booked seats stay booked even after you close the app.

**Stack:** Python (Tkinter) GUI · Java (JDBC) backend server · MySQL database

---

## ✨ Features

- **Visual seat map:** 6 rows × 8 seats with a walkway in the middle and a curved cinema screen at the top
- **Colour-coded seats:** grey = available, green = selected, red = booked
- **Automatic pricing:** the total updates after every click, based on the movie's ticket price and the number of seats
- **No double booking:** a seat already booked for a movie and show time cannot be booked again
- **Per-show availability:** the same seat is free again when you switch to a different show time
- **Ticket window:** shows name, movie, show, seats and total after booking
- **View All Bookings:** a table of every booking read from the database
- **Input checks:** friendly errors for a missing name, no seats selected, or a taken seat

## 🏗️ Architecture

```
┌──────────────────┐      ┌─────────────────────┐      ┌─────────────┐
│  Tkinter GUI     │ ───▶ │  Java backend       │ ───▶ │   MySQL     │
│ movie_booking.py │      │  (JDBC server)      │      │  database   │
└──────────────────┘      └─────────────────────┘      └─────────────┘
```

The GUI never talks to the database directly. It sends requests to the Java backend, which runs the SQL through JDBC.

## 📁 Project structure

```
movie-ticket-booking/
├── backend/            # Java server source + MySQL Connector/J JAR (lib/)
├── database.sql        # Creates the database and tables
├── movie_booking.py    # Tkinter GUI (entry point)
├── .gitignore
└── README.md
```

## ✅ Requirements

- Python 3 (Tkinter is included with the standard installer)
- Java JDK 11 or newer
- MySQL Server and MySQL Workbench
- MySQL Connector/J 9.5.0 (already included in `backend/lib`)

## 🚀 Getting started

### 1. Set up the database

Open `database.sql` in MySQL Workbench and run it **once**.

### 2. Start the Java backend

The password is read from an environment variable so it never goes into the repository. In PowerShell, from the project folder:

```powershell
$env:DB_USER = "theatre"
$env:DB_PASSWORD = "your_password_here"
cd backend
# compile and start MovieTicketServer (include lib/* on the classpath)
```

Default connection settings:

| Setting  | Value                  |
|----------|------------------------|
| Host     | `localhost`            |
| Port     | `3307`                 |
| Database | `movie_ticket_booking` |
| User     | `theatre`              |

### 3. Run the GUI

In a **second terminal**, from the project folder:

```bash
python movie_booking.py
```

## 🖱️ How to use

1. Enter your name
2. Choose a movie and a show time
3. Click free seats; they turn green and the total updates
4. Click **Book Tickets**; a ticket opens and your seats turn red
5. Use **Clear Selection** to start over, or **View All Bookings** to see saved bookings

## 🧪 Test checklist

- [ ] Click a seat, then click it again → it unselects
- [ ] Book with no name → error message
- [ ] Book with no seats → error message
- [ ] Book a seat → saved, turns red, ticket opens
- [ ] Click a booked seat → "already booked" warning
- [ ] Switch show time → the seat is free again
- [ ] Close and reopen the app → booked seats are still red

## 🔮 Future improvements

- Online payment
- Printable PDF tickets
- Cancel a booking
- Admin login to add movies
- Different prices for different rows

