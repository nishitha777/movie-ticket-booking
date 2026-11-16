"""Tkinter screen for the movie booking JDBC/MySQL project."""

import tkinter as tk
from tkinter import messagebox, ttk
from urllib.parse import urlencode
from urllib.request import Request, urlopen

API = "http://127.0.0.1:8080/api"
MOVIES = {"Space Rangers": (1, 180), "Ocean Mystery": (2, 150), "Funny Neighbours": (3, 120)}
SHOW_TIMES = ["6:00 PM", "9:00 PM"]
ROWS = "ABCDEF"


def api(path, data=None):
    body = urlencode(data).encode() if data is not None else None
    request = Request(API + path, data=body, headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urlopen(request, timeout=3) as response:
        return response.read().decode()


def get_booked_seats():
    movie_id = MOVIES[movie_choice.get()][0]
    query = urlencode({"movieId": movie_id, "showTime": time_choice.get()})
    booked = api("/seats?" + query)
    return booked.split(",") if booked else []


def update_summary():
    total = len(selected_seats) * MOVIES[movie_choice.get()][1]
    seats_text.set(", ".join(sorted(selected_seats)) if selected_seats else "None")
    total_text.set(f"Total: ₹{total}")


def draw_seats():
    try:
        booked = get_booked_seats()
        for seat, button in seat_buttons.items():
            if seat in booked:
                button.config(bg="#e74348", state="disabled")
            else:
                button.config(bg="#18bd70" if seat in selected_seats else "#484a59", state="normal")
        update_summary()
    except Exception:
        messagebox.showerror("Database connection", "Start the Java JDBC server and check MySQL.")


def click_seat(seat):
    if seat in selected_seats:
        selected_seats.remove(seat)
    else:
        selected_seats.add(seat)
    draw_seats()


def book_tickets():
    name = name_entry.get().strip()
    if not name or not selected_seats:
        messagebox.showerror("Booking", "Enter your name and select seats.")
        return
    movie_id, price = MOVIES[movie_choice.get()]
    seats = sorted(selected_seats)
    try:
        api("/bookings", {"name": name, "movieId": movie_id,
                           "showTime": time_choice.get(), "seats": ",".join(seats)})
    except Exception:
        messagebox.showerror("Booking", "Booking failed. Check MySQL and make sure the seats are free.")
        draw_seats()
        return
    messagebox.showinfo("Ticket booked", f"{movie_choice.get()}\nShow: {time_choice.get()}\n"
                        f"Seats: {', '.join(seats)}\nTotal: ₹{len(seats) * price}")
    selected_seats.clear()
    draw_seats()


def selection_changed(_event=None):
    selected_seats.clear()
    draw_seats()


window = tk.Tk()
window.title("Movie Ticket Booking")
window.geometry("800x510")
window.configure(bg="#191a29")

tk.Label(window, text="MOVIE TICKET BOOKING", bg="#191a29", fg="#f3bd18",
         font=("Arial", 20, "bold")).pack(pady=15)
main = tk.Frame(window, bg="#191a29")
main.pack(fill="both", expand=True, padx=24, pady=10)
form = tk.Frame(main, bg="#252638", padx=16, pady=14)
form.pack(side="left", fill="y", padx=(0, 25))
tk.Label(form, text="Your name", bg="#252638", fg="white").pack(anchor="w")
name_entry = tk.Entry(form)
name_entry.pack(fill="x", pady=5)
tk.Label(form, text="Movie", bg="#252638", fg="white").pack(anchor="w")
movie_choice = tk.StringVar(value="Space Rangers")
movie_box = ttk.Combobox(form, textvariable=movie_choice, values=list(MOVIES), state="readonly")
movie_box.pack(fill="x", pady=5)
tk.Label(form, text="Show time", bg="#252638", fg="white").pack(anchor="w")
time_choice = tk.StringVar(value=SHOW_TIMES[0])
time_box = ttk.Combobox(form, textvariable=time_choice, values=SHOW_TIMES, state="readonly")
time_box.pack(fill="x", pady=5)
price_label = tk.Label(form, bg="#252638", fg="white")
price_label.pack(anchor="w", pady=5)
seats_text = tk.StringVar(value="None")
tk.Label(form, text="Seats:", bg="#252638", fg="white").pack(anchor="w")
tk.Label(form, textvariable=seats_text, bg="#252638", fg="white").pack(anchor="w")
total_text = tk.StringVar(value="Total: ₹0")
tk.Label(form, textvariable=total_text, bg="#252638", fg="#f3bd18",
         font=("Arial", 14, "bold")).pack(anchor="w", pady=8)
tk.Button(form, text="BOOK TICKETS", command=book_tickets).pack(fill="x", pady=4)

seat_area = tk.Frame(main, bg="#191a29")
seat_area.pack(side="left", fill="both", expand=True)
tk.Label(seat_area, text="SCREEN THIS WAY", bg="#191a29", fg="#f3bd18").pack(pady=8)
selected_seats = set()
seat_buttons = {}
grid = tk.Frame(seat_area, bg="#191a29")
grid.pack()
for row_index, row in enumerate(ROWS):
    tk.Label(grid, text=row, bg="#191a29", fg="white").grid(row=row_index, column=0)
    for number in range(1, 9):
        seat = f"{row}{number}"
        col = number + (1 if number > 4 else 0)
        button = tk.Button(grid, text=seat, width=4, bg="#484a59", fg="white",
                           command=lambda seat=seat: click_seat(seat))
        button.grid(row=row_index, column=col, padx=3, pady=3)
        seat_buttons[seat] = button
tk.Label(seat_area, text="Available     Selected     Booked", bg="#191a29", fg="white").pack(pady=12)
movie_box.bind("<<ComboboxSelected>>", lambda _e: (selection_changed(), price_label.config(
    text=f"Ticket price: ₹{MOVIES[movie_choice.get()][1]}")))
time_box.bind("<<ComboboxSelected>>", selection_changed)
price_label.config(text=f"Ticket price: ₹{MOVIES[movie_choice.get()][1]}")
draw_seats()
window.mainloop()
