# Twitter Clone - Project Report

## 1. Introduction

This project is a desktop social networking application inspired by X (formerly Twitter). The application was developed as the final project for the Advanced Programming course using Java. The system follows a client–server architecture and allows multiple users to communicate with a central server through TCP sockets. Persistent data is stored in a PostgreSQL database.

The project was implemented collaboratively using GitHub. Each team member contributed to different parts of the application, including backend development, database integration, networking, and graphical user interface implementation.

## 2. System Architecture

### Overview

The application follows a layered Client–Server architecture.

The client is responsible for displaying the user interface and collecting user actions. The server processes incoming requests, performs business logic, communicates with the database, and sends responses back to clients.

### Client Side

The client application was implemented using JavaFX.

Main responsibilities include:
- Login and registration interface
- Timeline and tweet display
- Sending requests to the server
- Receiving responses
- User profile management
- Media presentation

The application starts from:
```
client.Launcher
```
which launches the JavaFX application.

### Server Side

The server starts from:
```
server.network.Server
```

The server:
- Opens a `ServerSocket`
- Accepts multiple client connections
- Uses a thread pool for concurrent clients
- Initializes the database
- Processes requests
- Returns JSON responses

Each connected client is handled independently.

### Communication Protocol

Communication between client and server is implemented using JSON messages over TCP sockets.

Each request contains:
- Request type
- Required parameters
- Authentication information when necessary

The server validates every request and sends an appropriate response.

## 3. Project Structure

The project is organized into several packages.

| Package | Description |
|---|---|
| `client` | Contains JavaFX interface, controllers, helper classes and networking components. |
| `server.network` | Responsible for socket communication and client management. |
| `server.database` | Contains database initialization and JDBC utilities. |
| `server.controllers` | Implements business logic and request handling. |
| `shared.models` | Contains model classes shared between client and server. |
| `shared.protocol` | Contains protocol objects exchanged between client and server. |

This separation improves maintainability and readability.

## 4. Database Design

The application uses PostgreSQL.

Database initialization is performed through:
```
DatabaseInitializer
```

The SQL schema creates the database tables.

Major entities include:
- Users
- Tweets
- Likes
- Follows
- Sessions
- Hashtags
- TweetHashtags
- Media

Relationships:
- One user can create many tweets.
- Users can follow many other users.
- Tweets can receive multiple likes.
- Tweets may contain multiple hashtags.
- A tweet may contain multiple media files.

## 5. Object-Oriented Design

The project applies several object-oriented principles.

**Encapsulation**
Each model class stores its own data and exposes it through methods.
Examples include:
- `User`
- `Tweet`
- `Session`

**Composition**
The server is composed of multiple components including controllers, networking classes and database utilities.

**Modularity**
The application separates GUI, networking, business logic and persistence into different packages. This reduces coupling between components.

## 6. Concurrency

The server supports multiple simultaneous users.

A cached thread pool is used so every connected client runs independently.

Advantages include:
- Multiple concurrent logins
- Simultaneous tweet publishing
- Better scalability
- Separation between client sessions

## 7. Technologies Used

| Category | Technology |
|---|---|
| Programming Language | Java |
| GUI | JavaFX |
| Database | PostgreSQL |
| Database Access | JDBC |
| Networking | Java Socket API |
| Serialization | JSON |
| Version Control | Git & GitHub |

## 8. Features Implemented

The project implements the major required functionalities including:
- User registration
- User login
- Session management
- Profile management
- Tweet creation
- Tweet deletion
- Tweet timeline
- Follow / Unfollow
- Like tweets
- Search
- Hashtag support
- Image attachments
- Multi-client support
- PostgreSQL persistence

## 9. Challenges

During development the team faced several challenges:
- Synchronizing multiple connected users
- Managing concurrent database access
- Organizing JavaFX controllers
- Separating frontend and backend responsibilities

These issues were solved through modular design and incremental testing.

## 10. AI Usage Disclosure

AI tools were used as programming assistants.

**Tools:**
- ChatGPT (OpenAI)
- Gemini

**Purpose:**
- Debugging
- Code explanation
- Documentation writing
- Small code suggestions

**Extent of usage:**
AI-generated content was reviewed and modified before being included in the project. All architectural decisions, implementation details and testing were performed by the team members.

AI tools were also used as a learning aid to better understand core concepts such as networking, concurrency, and database integration throughout the development process.

## 11. Conclusion

This project demonstrates the implementation of a desktop social networking platform using Java, JavaFX, TCP socket programming and PostgreSQL.

The final system provides a complete client–server application supporting authentication, tweet management, social interactions and persistent storage while following object-oriented software engineering principles.