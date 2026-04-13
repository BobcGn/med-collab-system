# frontend

This frontend is now a pure React client.

## Scripts

```sh
npm install
npm run dev
npm run build
npm run preview
```

## Structure

- `src/main.jsx`: React entry
- `src/App.jsx`: main application, custom routing, layouts and pages
- `src/lib`: API, auth, runtime config, notifications and WebSocket helpers
- `src/app.css`: shared client styles

All Vue pages and Vue router dependencies have been removed from the client package.
