# EV Co-Ownership Management System

A comprehensive backend system for managing electric vehicle (EV) co-ownership groups, enabling multiple users to share ownership, schedule usage, handle payments, and manage vehicle maintenance collaboratively.

## 🚀 Overview

This is a full-stack backend application built with Spring Boot that facilitates the co-ownership of electric vehicles. The system allows multiple users to form ownership groups, manage their shares, book vehicle usage time slots, handle financial transactions, and coordinate maintenance activities.

### Key Highlights

- **Complex Business Logic**: Handles ownership percentages, quota management, booking conflicts, and financial calculations
- **Payment Integration**: Integrated with VNPay for secure payment processing
- **Real-time Communication**: WebSocket support for live notifications and updates
- **OCR Capabilities**: Google Cloud Vision API integration for document processing
- **Cloud Storage**: Cloudflare R2 integration for file storage
- **Comprehensive API**: 34+ REST controllers with 400+ endpoints
- **Production Ready**: Deployed on Render with PostgreSQL database

## ✨ Features

### Core Functionality

- **User Management & Authentication**
  - JWT-based authentication with refresh tokens
  - Role-based access control (ADMIN, STAFF, CO_OWNER)
  - OTP verification via email and SMS (Twilio)
  - User profile management with document upload

- **Ownership Group Management**
  - Create and manage co-ownership groups
  - Ownership share distribution with percentage validation
  - Group member invitations and approvals
  - Contract generation and digital signing workflow

- **Vehicle Management**
  - Vehicle registration and information management
  - Vehicle image upload and approval workflow
  - Vehicle status tracking (Good, Under Maintenance, Has Issues)
  - Vehicle check-in/check-out system with QR codes

- **Booking System**
  - Weekly calendar view with time slot management
  - Flexible booking system supporting overnight bookings
  - Quota management based on ownership percentage
  - Booking conflict detection and prevention
  - Check-in/check-out with QR code generation

- **Financial Management**
  - Shared fund management for groups
  - Payment processing via VNPay integration
  - Deposit collection and tracking
  - Expense tracking and approval workflow
  - Financial reports generation
  - Maintenance payment handling

- **Maintenance & Incident Management**
  - Maintenance request and approval workflow
  - Maintenance scheduling and tracking
  - Incident reporting and resolution
  - Vehicle check records (pre-use and post-use)
  - Maintenance payment processing

- **Notifications & Communication**
  - Real-time notifications via WebSocket
  - Email notifications (Thymeleaf templates)
  - SMS notifications via Twilio
  - Notification history and management

- **Document Processing**
  - OCR integration with Google Cloud Vision API
  - Document upload and verification
  - Vehicle document extraction and validation

- **Admin Dashboard**
  - System-wide analytics and reporting
  - User management and moderation
  - Contract approval and management
  - Audit logging

## 🛠️ Tech Stack

### Backend Framework
- **Spring Boot 3.5.6** - Main application framework
- **Java 17** - Programming language
- **Maven** - Dependency management and build tool

### Database & Persistence
- **PostgreSQL** - Primary relational database
- **Spring Data JPA / Hibernate** - ORM framework
- **H2 Database** - In-memory database for testing

### Security
- **Spring Security** - Authentication and authorization
- **JWT (JJWT)** - Token-based authentication
- **BCrypt** - Password hashing

### Third-Party Integrations
- **VNPay** - Payment gateway integration
- **Google Cloud Vision API** - OCR and image analysis
- **Cloudflare R2** - Object storage (S3-compatible)
- **Twilio** - SMS messaging service
- **Spring Mail** - Email service

### API Documentation
- **SpringDoc OpenAPI 3** - API documentation (Swagger UI)

### Real-time Communication
- **WebSocket** - Real-time bidirectional communication

### Additional Libraries
- **Lombok** - Reducing boilerplate code
- **Thymeleaf** - Template engine for email
- **Thumbnailator** - Image processing
- **Apache Commons Lang3** - Utility functions
- **Jackson** - JSON processing

## 📁 Project Structure

```
ev-co-ownership-be/
├── src/
│   ├── main/
│   │   ├── java/com/group8/evcoownership/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST API controllers (34 controllers)
│   │   │   ├── dto/             # Data Transfer Objects (141 DTOs)
│   │   │   ├── entity/          # JPA entities (25 entities)
│   │   │   ├── enums/           # Enumeration types (24 enums)
│   │   │   ├── exception/       # Custom exception handlers
│   │   │   ├── filter/          # Security filters
│   │   │   ├── repository/      # Data access layer (23 repositories)
│   │   │   ├── service/         # Business logic layer (43 services)
│   │   │   ├── utils/           # Utility classes
│   │   │   └── validation/      # Custom validators
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-prod.properties
│   │       ├── application-test.properties
│   │       └── templates/       # Email templates
│   └── test/
│       └── java/                # Unit and integration tests
├── pom.xml                      # Maven configuration
├── Dockerfile                   # Container configuration
├── render.yaml                   # Render deployment config
└── README.md                     # This file
```

## 🗄️ Database Schema

The system uses PostgreSQL with a comprehensive schema including:

- **Users & Roles** - User management and role-based access
- **OwnershipGroup** - Co-ownership group information
- **OwnershipShare** - User ownership percentages per group
- **Vehicle** - Vehicle information and details
- **UsageBooking** - Vehicle booking records
- **SharedFund** - Group financial accounts
- **Payment** - Payment transaction records
- **Maintenance** - Maintenance requests and records
- **Incident** - Incident reports
- **VehicleCheck** - Pre-use and post-use vehicle inspections
- **Contract** - Legal contracts and agreements
- **Notification** - System notifications
- **Voting** - Group voting system
- And more...

See `database_schema.sql` and `dbdiagram_schema.dbml` for detailed schema documentation.

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+
- (Optional) Docker for containerized deployment

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/ductruong2810/ev-co-ownership-be.git
   cd ev-co-ownership-be
   ```

2. **Configure database**
   - Create a PostgreSQL database
   - Update `src/main/resources/application.properties` with your database credentials:
     ```properties
     spring.datasource.url=jdbc:postgresql://localhost:5432/evshare
     spring.datasource.username=your_username
     spring.datasource.password=your_password
     ```

3. **Configure environment variables**
   Copy the example environment file and fill in your values:
   ```bash
   cp .env.example .env
   ```
   
   Then edit `.env` file with your actual configuration values. See `.env.example` for all required environment variables:
   - Database credentials (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`)
   - JWT secret (`JWT_SECRET`)
   - Email password (`SPRING_MAIL_PASSWORD`)
   - VNPay secret key (`VN_PAY_SECRET_KEY`)
   - Cloudflare R2 credentials (optional, for file storage)
   - Google Cloud Vision API key (optional, for OCR)
   
   **⚠️ Important**: Never commit the `.env` file to version control. It contains sensitive information.

4. **Run database migrations**
   ```bash
   # Execute database_schema.sql to create tables
   psql -U your_username -d evshare -f database_schema.sql
   ```

5. **Build the project**
   ```bash
   ./mvnw clean install
   ```

6. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

   Or using the JAR file:
   ```bash
   java -jar target/ev-co-ownership-be-0.0.1-SNAPSHOT.jar
   ```

7. **Access the application**
   - API Base URL: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - Actuator Health: `http://localhost:8080/actuator/health`

## 🧪 Testing

The project includes comprehensive test suites:

- **Unit Tests** - Service layer testing with Mockito
- **Integration Tests** - Full Spring Boot context testing
- **Test Configuration** - Separate test profile with H2 in-memory database

Run tests:
```bash
./mvnw test
```

## 📚 API Documentation

Once the application is running, access the interactive API documentation at:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

The API includes:
- 34+ REST controllers
- 400+ endpoints
- Comprehensive request/response DTOs
- Authentication and authorization documentation

### Example API Endpoints

- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User authentication
- `GET /api/calendar/groups/{groupId}/weekly` - Get weekly calendar
- `POST /api/calendar/flexible-booking` - Create booking
- `GET /api/vehicles` - List vehicles
- `POST /api/payments/vnpay/create` - Create payment URL
- `GET /api/maintenance` - List maintenance requests

## 🐳 Docker Deployment

Build and run with Docker:

```bash
# Build the image
docker build -t ev-co-ownership-be .

# Run the container
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
  -e JWT_SECRET=your_secret \
  ev-co-ownership-be
```

## ☁️ Cloud Deployment

The application is configured for deployment on **Render**:

- Configuration file: `render.yaml`
- Production profile: `application-prod.properties`
- Health check endpoint: `/actuator/health`
- Environment variables configured via Render dashboard

### Deployment Steps

1. Connect your GitHub repository to Render
2. Configure environment variables in Render dashboard
3. Render will automatically build and deploy using `render.yaml`
4. The application will be available at your Render URL

## 🔒 Security Features

- **JWT Authentication** - Secure token-based authentication
- **Role-Based Access Control** - Fine-grained permissions
- **Password Encryption** - BCrypt hashing
- **SQL Injection Prevention** - Parameterized queries via JPA
- **CORS Configuration** - Cross-origin resource sharing
- **Input Validation** - Bean validation and custom validators
- **Audit Logging** - Track important system events

## 📊 Key Business Logic Highlights

### Ownership Percentage Validation
- Ensures total ownership percentages equal exactly 100%
- Validates individual ownership percentages are greater than 0%
- Prevents contract generation with invalid ownership distribution

### Quota Management
- Calculates weekly usage quota based on ownership percentage
- Tracks used and remaining quota slots
- Prevents over-booking beyond allocated quota

### Booking Conflict Prevention
- Detects overlapping bookings
- Prevents double-booking of time slots
- Handles overnight bookings across date boundaries

### Financial Transaction Management
- Optimistic locking for concurrent payment processing
- Transaction rollback on failures
- Payment status tracking and reconciliation

## 🤝 Contributing

This is a personal project, but suggestions and feedback are welcome!

## 📝 License

This project is proprietary and confidential.

## 👤 Author

**Truong Hoang**
- GitHub: [@ductruong2810](https://github.com/ductruong2810)
- Project Repository: [ev-co-ownership-be](https://github.com/ductruong2810/ev-co-ownership-be)

## 🙏 Acknowledgments

- Spring Boot community
- PostgreSQL documentation
- All third-party service providers (VNPay, Google Cloud, Cloudflare, Twilio)

---

**Note**: This is a production-ready backend system demonstrating enterprise-level Spring Boot development, complex business logic implementation, third-party integrations, and scalable architecture patterns.

