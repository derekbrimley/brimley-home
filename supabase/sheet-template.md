# The master sheet

Create a Google Sheet, share it (Editor) with the service account's `client_email`,
and put its id in `GOOGLE_SHEET_ID`. Six tabs, header row on each. Columns the app
fills are marked *(app)*.

| Tab | Columns |
|---|---|
| Watch | Title · Shelf (Shows / Movies / Videos / Listen) · Found on *(app)* · Link *(app)* |
| Listen | Podcast · Feed URL (optional) |
| Jobs | Kid · Job · Days (e.g. `Mon, Wed, Fri`, `weekdays`, `daily`) |
| Bounties | Job · Amount · Status (blank = open; the tablet writes `waiting`; you write `paid`) · Kid (optional) |
| Makes | Title · Who · Link · Picture URL |
| Services | Service (one per row, in order of preference: Disney+, Max, Netflix, Prime…) |

For Videos, put the YouTube link in the Title column; the app swaps in the real title.
