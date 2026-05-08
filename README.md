# Group-49 Good food and healthy eating.

Worrying trends such as increasing childhood and adult obesity highlight the need to improve public diet and eating habits. This project aims to develop a software system that supports healthier lifestyles through diet monitoring and nutritional guidance.
The system will initially focus on diet tracking and personalised advice, with potential expansion into support for home cooking.


## Features

Client and professional sign up, login, and onboarding quizzes
Role-based access control for client and professional areas
Client dashboard with calorie tracking, macro tracking, nutrition guidance, monthly diet trends, and 7-day nutrition trends
Client profile viewing and updating
Food logging for foods and recipes, with saved meals and diary saving
Recipe browsing, favouriting, and 1 to 5 star reviews
Browse professionals as a client
Link clients to professionals with consent-based data sharing
Professional dashboard showing linked clients
Professional view of client profile and diet details
Client-professional messaging
Landing page

## Running the App
1. "cd .\Group49-\"
2. "$env:DB_PASSWORD = "npg_6ilmt7DUuZBY"
3. "./gradlew build"
4. "./gradlew run"

## Project Structure

- `app/src/main/kotlin/diettracker/` - main Kotlin application code
- `app/src/main/kotlin/diettracker/routing/` - route definitions for pages and features
- `app/src/main/kotlin/diettracker/services/` - business logic and service-layer code
- `app/src/main/kotlin/diettracker/db/` - database configuration, repositories, and table definitions
- `app/src/main/resources/templates/` - Pebble HTML templates
- `app/src/main/resources/static/` - static assets such as CSS, JavaScript, and images
- `app/src/test/kotlin/` - automated tests
- `app/build.gradle.kts` - app module Gradle build configuration
- `README.md` - project documentation

## AI

AI was used to support parts of the frontend development in this project.
Where AI-assisted code or ideas were used, this is indicated in the code comments at the relevant sections.

## Testing

The project includes automated testing and code quality checks as part of the build process.

These include:
- automated tests 
- ktlint for code style checking     ".\gradlew ktlintCheck"
- detekt for static analysis         ".\gradlew detekt"

Running the build will execute these automated checks.

## Contributors
- Luca Zeolla
- Lewis Wiles
- Fareedah Fashola
- Tianqi Wang
- Henry Wang

## Limitations

- Some foods and recipes added during database seeding contain 0 calorie values or incomplete nutritional information. This happens because parts of the source dataset did not provide full nutrition data, so a small number of seeded entries may be inaccurate or incomplete.

- Due to the limited project timeframe, some decisions were made to support rapid development and ensure the main features were completed. This meant prioritising working functionality over more extensive refinement, optimisation, and polish.