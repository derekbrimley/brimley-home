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

Makes show on the Fridge (tap the Note card), in sheet order. A Picture can be a
Google Drive share link if the file is shared "anyone with the link". For a Scratch
game, use the project's embed link (`https://scratch.mit.edu/projects/<id>/embed`): it
fills the screen, and the tablet only lets a make's page move around its own site.
