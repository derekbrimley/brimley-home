-- brimley-home: run in the Supabase SQL editor. Parents edit the Google Sheet;
-- these tables hold what the sheet can't: state, caches and posted cards.

CREATE TABLE public.kv (
  key TEXT PRIMARY KEY,
  value JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Resolved titles from the Watch and Listen tabs (cache of TMDB/JustWatch/iTunes lookups).
CREATE TABLE public.catalog_items (
  source_key TEXT PRIMARY KEY,
  shelf TEXT NOT NULL CHECK (shelf IN ('shows', 'movies', 'videos', 'listen')),
  title TEXT NOT NULL,
  service TEXT,
  link TEXT,
  poster_url TEXT,
  year INTEGER,
  runtime_minutes INTEGER,
  resolved_at TIMESTAMPTZ,
  sheet_row INTEGER NOT NULL DEFAULT 0
);

-- A job ticked on a given day. job_id is "<kid>|<title>" slugged (lib/jobs.ts).
CREATE TABLE public.job_completions (
  date DATE NOT NULL,
  job_id TEXT NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (date, job_id)
);

CREATE TABLE public.bounty_claims (
  id SERIAL PRIMARY KEY,
  sheet_row INTEGER NOT NULL,
  title TEXT NOT NULL,
  kid TEXT,
  amount_cents INTEGER NOT NULL DEFAULT 0,
  claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Day cards: lunch menu (posted by Zo), notes and drawings (from the tablet),
-- countdowns and birthdays (from a phone).
CREATE TABLE public.cards (
  id SERIAL PRIMARY KEY,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT,
  data JSONB,
  show_from TIMESTAMPTZ,
  show_until TIMESTAMPTZ,
  priority INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL DEFAULT 'api',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX cards_window ON public.cards (show_from, show_until);

-- Allowance ledger, for when bounties are approved (status "paid" in the sheet).
CREATE TABLE public.ledger (
  id SERIAL PRIMARY KEY,
  kid TEXT NOT NULL,
  amount_cents INTEGER NOT NULL,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.kv ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.job_completions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bounty_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ledger ENABLE ROW LEVEL SECURITY;
-- No policies: only the service role (the API) reads or writes.
