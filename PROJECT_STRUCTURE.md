# Project Structure

## Tech Stack

- **Backend**
  - Spring Boot (Java)
- **Frontend**
  - Flutter (Dart)

## Critical Architecture

- **Flat DTO pattern for Consultations**
  - Consultation payloads returned by the backend are intentionally flattened into a single DTO (the “Flat DTO”) that includes consultation fields plus the key patient/vitals fields.
  - This exists to avoid frontend issues caused by nested/optional structures and inconsistent serialization.

## Database / Domain Model Relationships

- **Patient**
  - A `Patient` represents either:
    - a `USER` (links to `User`), or
    - a `FAMILY_MEMBER` (links to `FamilyMember`).
  - Patient demographic/vitals fields may come from `User` / `FamilyMember` identity fields and/or `medicalInfo`.

## Service Rules

- **Only `com.omnicare` is authoritative**
  - All backend work must happen under the `com.omnicare` package.
  - The `com.omnilinks` package is **not** used for current backend routing/logic and must be ignored for implementation work.
