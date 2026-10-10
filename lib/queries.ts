import { supabaseAdmin } from "./supabaseAdmin";
import type { CardRow } from "./cards";
import type { CatalogRow } from "./catalog";
import type { SheetData } from "./sheet";

// ── kv (sheet cache) ─────────────────────────────────────────────────────────

export async function getSheetCache(): Promise<SheetData | null> {
  const { data } = await supabaseAdmin().from("kv").select("value").eq("key", "sheet").maybeSingle();
  return (data?.value as SheetData | undefined) ?? null;
}

export async function setSheetCache(sheet: SheetData): Promise<void> {
  await supabaseAdmin().from("kv").upsert({ key: "sheet", value: sheet, updated_at: new Date().toISOString() }, { onConflict: "key" });
}

// ── catalog ──────────────────────────────────────────────────────────────────

export async function getCatalogRows(): Promise<CatalogRow[]> {
  const { data } = await supabaseAdmin().from("catalog_items").select("*");
  return (data as CatalogRow[] | null) ?? [];
}

export async function upsertCatalogRow(row: CatalogRow): Promise<void> {
  await supabaseAdmin().from("catalog_items").upsert(row, { onConflict: "source_key" });
}

export async function deleteCatalogRowsExcept(keys: string[]): Promise<void> {
  if (keys.length === 0) {
    await supabaseAdmin().from("catalog_items").delete().neq("source_key", "");
    return;
  }
  await supabaseAdmin().from("catalog_items").delete().not("source_key", "in", `(${keys.map((k) => `"${k}"`).join(",")})`);
}

// ── jobs ─────────────────────────────────────────────────────────────────────

export async function getCompletions(fromDate: string, toDate: string): Promise<Set<string>> {
  const { data } = await supabaseAdmin().from("job_completions").select("date, job_id").gte("date", fromDate).lte("date", toDate);
  return new Set((data ?? []).map((r) => `${r.date}|${r.job_id}`));
}

export async function setCompletion(date: string, jobId: string, done: boolean): Promise<void> {
  const db = supabaseAdmin();
  if (done) await db.from("job_completions").upsert({ date, job_id: jobId }, { onConflict: "date,job_id" });
  else await db.from("job_completions").delete().eq("date", date).eq("job_id", jobId);
}

export async function recordBountyClaim(row: number, title: string, kid: string | null, amountCents: number): Promise<void> {
  await supabaseAdmin().from("bounty_claims").insert({ sheet_row: row, title, kid, amount_cents: amountCents });
}

// ── cards ────────────────────────────────────────────────────────────────────

export async function getCardRows(): Promise<CardRow[]> {
  const { data } = await supabaseAdmin().from("cards").select("*").order("id", { ascending: false }).limit(100);
  return (data as CardRow[] | null) ?? [];
}

export async function insertCard(card: Omit<CardRow, "id">): Promise<CardRow> {
  const { data, error } = await supabaseAdmin().from("cards").insert(card).select().single();
  if (error) throw new Error(error.message);
  return data as CardRow;
}

export async function deleteCard(id: number): Promise<void> {
  await supabaseAdmin().from("cards").delete().eq("id", id);
}

// Drop earlier lunch menus for the same week, so a resend replaces rather than stacks.
export async function deleteLunchCards(weekOf: string): Promise<void> {
  const { error } = await supabaseAdmin().from("cards").delete().eq("kind", "lunch_menu").eq("data->>week_of", weekOf);
  if (error) throw new Error(error.message);
}
