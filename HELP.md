# SmartCheck setup

SmartCheck is a Spring Boot, Thymeleaf, and Tailwind-based student session tracker. Tutor and student records use PostgreSQL. The tutor dashboard manages tutor email addresses, student session lengths, and each student's alert recipients. Students can search their name and start a timer; tutors receive an email at the 10-minute threshold and another when the session time ends.

## Requirements

- Java 21
- PostgreSQL (free and open-source)
- Maven wrapper is included

## Install PostgreSQL on Windows

1. Download the Windows installer from [postgresql.org/download/windows](https://www.postgresql.org/download/windows/) and run it. The installer includes PostgreSQL Server and pgAdmin, a free graphical administration tool.
2. During setup, keep the default port `5432` and set a password for the `postgres` administrator.
3. Open pgAdmin and connect to the local server. Right-click **Login/Group Roles** → **Create** → **Login/Group Role**. Name it `smartcheck`, set a password, and enable **Can login**.
4. Right-click **Databases** → **Create** → **Database**. Name it `smartcheck` and set `smartcheck` as the owner.
5. Keep the default connection values (`localhost:5432`, database/user `smartcheck`). If your password differs from the development default, set `DB_PASSWORD` to that password before launching the application.
6. In pgAdmin's Query Tool, execute this SQL to set the `smartcheck` role password:

   ```sql
   ALTER ROLE smartcheck WITH LOGIN PASSWORD 'smartcheck';
   ```

Alternatively, create the user and database from the `psql` terminal as the PostgreSQL administrator:

```sql
CREATE USER smartcheck WITH PASSWORD 'choose-a-local-password';
CREATE DATABASE smartcheck OWNER smartcheck;
```

## Run locally

From the project directory in PowerShell:

```powershell
$env:DB_PASSWORD = "your-local-password"
.\mvnw.cmd spring-boot:run
```

Browse to [http://localhost:8080](http://localhost:8080). Hibernate creates/updates the application tables automatically during development.

Environment overrides are supported for deployments:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/smartcheck` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `smartcheck` | Database login |
| `DB_PASSWORD` | `smartcheck` | Database password |
| `REMINDERS_ENABLED` | `false` | Set to `true` to enable email alerts |
| `MAIL_HOST` | empty | SMTP host |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | SMTP credentials |
| `MAIL_FROM` | empty | Sender email address |

To enable reminders, configure the SMTP variables for a mail provider and set `REMINDERS_ENABLED=true`. Alert delivery is checked every 30 seconds. The app sends a warning once when 10 minutes remain and a final alert when the configured session expires. Failed SMTP delivery is logged and retried on a later check.

Tailwind is loaded from its CDN in the HTML templates for easy local development, so the browser needs internet access for styling. For an offline or production deployment, compile Tailwind into static assets and serve them locally. This starter does not yet include authentication; secure the tutor dashboard before exposing the application publicly.

## Verify

```powershell
.\mvnw.cmd test
```
