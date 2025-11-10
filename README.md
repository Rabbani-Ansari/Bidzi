# 🚖 BidZi

BidZi is a modern ride-hailing and bidding platform where customers place bids first, drivers respond with counter-offers, and users can even share rides or schedule future trips.
This system gives customers flexibility, transparency, and control over pricing — all in a sleek Kotlin-based Android app.

🧭 Overview

BidZi enhances the traditional ride-hailing experience with a bidding-based booking model.
Customers can propose their fare, view driver offers, accept or negotiate, share rides with others, or schedule a trip in advance.
It’s designed to be efficient, fair, and user-friendly for both customers and drivers.

---

## ✨ Features

* 💸 Customers place ride bids and get counter-offers from nearby drivers
* 🚘 Book a ride instantly after choosing the preferred driver
* 🗺️ Real-time location tracking and live trip updates
* 💳 In-app fare summary and payment overview
* ⭐ Ratings and reviews for better ride choices
* ⚡ Fast, reliable performance with Kotlin + Supabase integration

---

## 🧰 Tech Stack

| Layer            | Technology                        |
| ---------------- | --------------------------------- |
| **Language**     | Kotlin                            |
| **Architecture** | MVVM + Coroutines + LiveData      |
| **Backend**      | Supabase                          |
| **Database**     | Postgres (via Supabase)           |
| **UI**           | Material 3 Design Components      |
| **Tools**        | Android Studio, Jetpack Libraries |

---

## 🚀 Setup & Installation

1. Clone the repository

   ```bash
   git clone https://github.com/<your-username>/BidZi.git
   ```
2. Open the project in **Android Studio**
3. Add your **Supabase URL** and **API key** in `local.properties` or secure config
4. Sync Gradle and run the app on your emulator or physical device

---

## 🔒 Security Notes

* Do **not** expose your Supabase credentials in public repositories.
* Use `.env` or private configuration files excluded from Git control.

---

## 📱 Related Project

* [BidZiDriver (Driver App)](https://github.com/Rabbani-Ansari/BidZiDriver) – Driver-side companion app for handling customer bids and trips.

---

## 🧑‍💻 Author

Rabbani Ansari
Android Developer • Kotlin Enthusiast
[GitHub Profile](https://github.com/your-username)
