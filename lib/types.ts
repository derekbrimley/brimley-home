// Shared shapes. The Android app mirrors `Today` in model/Today.kt; keep them in step.

export type Shelf = "shows" | "movies" | "videos" | "listen";

export interface TodayEvent {
  start: string;          // ISO datetime, or YYYY-MM-DD for all-day
  end: string | null;
  title: string;
  who: string | null;     // family member short name, or null for everyone
  allDay: boolean;
}

export interface TodayJob {
  id: string;             // stable: "<kid>|<title>" slug
  kid: string;
  title: string;
  done: boolean;
}

export interface TodayBounty {
  row: number;            // row index in the Bounties tab (1-based, header excluded)
  title: string;
  amountCents: number;
  status: "open" | "waiting" | "paid";
  kid: string | null;
}

export interface JobsBlock {
  kid: string;
  jobs: TodayJob[];
  doneCount: number;
  allDone: boolean;
  starsThisWeek: number;  // days this week (Mon..today) with all jobs done
  bounty: TodayBounty | null;
}

export interface CatalogItem {
  id: string;             // sheet-derived slug
  shelf: Shelf;
  title: string;
  service: string;        // "disneyplus" | "max" | "netflix" | "prime" | "youtube" | "podcast"
  serviceLabel: string;   // "Disney+"
  posterUrl: string | null;
  link: string | null;    // deep link for the TV, YouTube URL, or RSS feed URL
  runtimeMinutes: number | null;
  year: number | null;
}

// A show's episodes, from GET /api/episodes?id=<catalog id>. Only episodes with
// their own link on the show's service; the tablet opens that link on the TV.
export interface ShowEpisode {
  number: number;
  title: string;
  link: string;
  stillUrl: string | null;
}

export interface ShowSeason {
  number: number;
  episodes: ShowEpisode[];
}

// The /api/episodes body. `note` says why there are no seasons, for the TV test screen.
export interface ShowEpisodes {
  seasons: ShowSeason[];
  note: string | null;
}

export interface Card {
  id: number;
  kind: string;           // lunch_menu | note | drawing | countdown | birthday
  title: string;
  body: string | null;
  data: Record<string, unknown> | null;
  showFrom: string | null;
  showUntil: string | null;
  priority: number;
  source: string;
}

export interface Playing {
  target: "kitchen" | "tv" | "yoto";
  title: string;
  subtitle: string | null;
  imageUrl: string | null;
  isPlaying: boolean;
  positionMs: number | null;
  durationMs: number | null;
}

export interface Weather {
  tempF: number;
  highF: number | null;
  summary: string;        // "cloudy, clearing by noon"
  kidLine: string;        // "jacket weather"
  icon: "sun" | "cloud" | "rain" | "snow" | "storm" | "fog";
}

export interface Make {
  title: string;
  who: string | null;
  link: string | null;
  imageUrl: string | null;
}

// An older note on the Fridge, from GET /api/notes. Drawings carry their PNG.
export interface FridgeNote {
  id: number;
  kind: "note" | "drawing";
  body: string | null;
  png: string | null;     // base64 PNG for a drawing
  createdAt: string;
}

export interface Today {
  generatedAt: string;
  date: string;           // YYYY-MM-DD in HOME_TZ
  tz: string;
  weather: Weather | null;
  events: TodayEvent[];
  tomorrowFirst: TodayEvent | null;
  jobs: JobsBlock[];
  cards: Card[];          // at most one note or drawing: the newest
  playing: Playing[];
  catalog: CatalogItem[];
  makes: Make[];
  warnings: string[];     // sources that failed; the tablet shows nothing for them
}
