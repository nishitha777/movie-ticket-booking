CREATE DATABASE IF NOT EXISTS movie_ticket_booking;
USE movie_ticket_booking;

CREATE TABLE IF NOT EXISTS movies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(8,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    movie_id INT NOT NULL,
    show_time VARCHAR(20) NOT NULL,
    seat VARCHAR(5) NOT NULL,
    price DECIMAL(8,2) NOT NULL,
    booked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (movie_id, show_time, seat),
    FOREIGN KEY (movie_id) REFERENCES movies(id)
);

INSERT INTO movies (name, price)
SELECT 'Space Rangers', 180 WHERE NOT EXISTS (SELECT 1 FROM movies WHERE name = 'Space Rangers');
INSERT INTO movies (name, price)
SELECT 'Ocean Mystery', 150 WHERE NOT EXISTS (SELECT 1 FROM movies WHERE name = 'Ocean Mystery');
INSERT INTO movies (name, price)
SELECT 'Funny Neighbours', 120 WHERE NOT EXISTS (SELECT 1 FROM movies WHERE name = 'Funny Neighbours');

SELECT * FROM movies;
SELECT * FROM bookings;
