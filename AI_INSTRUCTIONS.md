# AI Instructions

## Project Context
- **Name**: Paychat
- **Type**: Flutter mobile app
- **Core features**: TBD (presumably payments and chatting)
- **Tech stack**: Flutter, Firebase (Auth, Firestore), Riverpod (State Management)

## Architecture
- `lib/core`: App-wide utilities, constants, themes
- `lib/models`: Data models
- `lib/services`: APIs, external services (Firebase)
- `lib/providers`: Riverpod state providers
- `lib/screens`: Full-page views
- `lib/widgets`: Reusable UI components

## Rules
- Use Riverpod for state management.
- Prefer stateless widgets where possible.
- Ensure all styling aligns with a premium SaaS aesthetic.
