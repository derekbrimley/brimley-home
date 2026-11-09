import { describe, expect, it } from "vitest";
import { addLunchPhotos, matchPhoto, menuPhotos } from "./lunchPhotos";

const PAGE = "https://www.graniteschools.org/nutritionservices/school-menus/elementary-lunch-menu/";
const UP = "https://www.graniteschools.org/nutritionservices/wp-content/uploads/sites/20";

// Shaped like the district's calendar: thumbnails with the dish name as alt text.
const html = `
<table><tr>
<td><a href="#">Hot Dog with Tater Tots</a>
  <img width="150" height="150" src="${UP}/2025/09/Hot-Dog-with-Tater-Tots-1-150x150.png" alt="Hot Dog with Tater Tots">
  <img src="${UP}/2025/09/Images-for-Website-17.png" alt="Chili &amp; Cheese Hot Dog with Tater Tots">
  <img src="${UP}/2025/08/Screenshot-2025-08-04-110421-150x150.jpg" alt="PB&amp;J Uncrustable">
</td>
<td><img src="${UP}/2025/09/Beef-and-Cheese-Nachos.png" alt="Beef and Cheese Nachos">
  <img src="${UP}/2025/09/Cheese-Nachos.png" alt="Cheese Nachos"></td>
<td><img src="${UP}/2026/08/Chicken-Tenders-3-150x150.png" alt="Chicken Tenders">
  <img src="${UP}/2026/07/Waffles-WG-150x150.png" alt="Waffles WG"></td>
<td><img data-src="${UP}/2026/09/Teriyaki-Chicken.png" src="data:image/gif;base64,R0lGOD" alt="Teriyaki Chicken with Rice and Egg Roll"></td>
<td><img src="${UP}/2025/09/Cheese-or-Pepperoni-Pizza-Rippers.png" alt="Cheese or Pepperoni Pizza Rippers">
  <img src="${UP}/2025/08/Cheese-Pizza-Rippers-150x150.png" alt="Cheese Pizza Rippers">
  <img src="${UP}/2026/09/Chicken-Alfredo-Bake.png" alt="Chicken Alfredo Pasta Bake, Elem"></td>
<td><img src="/nutritionservices/wp-content/themes/gsd/icons/gluten-free.svg" alt="Gluten-Free"></td>
</tr></table>`;

const photos = menuPhotos(html, PAGE);
const match = (menu: string) => matchPhoto(menu, photos);

describe("menuPhotos", () => {
  it("takes full-size food pictures and skips icons and placeholders", () => {
    const urls = photos.map((p) => p.url);
    expect(urls).toContain(`${UP}/2025/09/Hot-Dog-with-Tater-Tots-1.png`);
    expect(urls).toContain(`${UP}/2026/09/Teriyaki-Chicken.png`);
    expect(urls.some((u) => u.endsWith(".svg") || u.startsWith("data:") || /-150x150/.test(u))).toBe(false);
  });
});

describe("matchPhoto", () => {
  it("matches Zo's wording to the district's dish names", () => {
    expect(match("Hot Dog or Chili Dog with Tater Tots")).toBe(`${UP}/2025/09/Images-for-Website-17.png`);
    expect(match("Beef & Cheese Nachos")).toBe(`${UP}/2025/09/Beef-and-Cheese-Nachos.png`);
    expect(match("Teriyaki Chicken w/ Rice and Egg Roll + scratch-made dessert")).toBe(`${UP}/2026/09/Teriyaki-Chicken.png`);
    expect(match("Cheese or Pepperoni Pizza Rippers")).toBe(`${UP}/2025/09/Cheese-or-Pepperoni-Pizza-Rippers.png`);
    expect(match("Chicken Alfredo Bake")).toBe(`${UP}/2026/09/Chicken-Alfredo-Bake.png`);
  });

  it("leaves a day without a photo when nothing is close", () => {
    expect(match("Chicken and Waffles")).toBeUndefined(); // two separate pictures, neither is the dish
    expect(match("Sloppy Joe (+ scratch-made dessert)")).toBeUndefined();
  });
});

// The real page (Nov 2026): each meal's pop-up has a hero image, and its parts have thumbnails.
const hero = (file: string, alt: string) =>
  `<img loading="lazy" decoding="async" width="1000" height="1000" src="${UP}/${file}" class="meal-hero-image wp-post-image" alt="${alt}" data-attachment-id="1" />`;
const thumb = (file: string, alt: string) =>
  `<img loading="lazy" decoding="async" width="150" height="150" src="${UP}/${file}" class="gsd-acc-thumb wp-post-image" alt="${alt}" style="width:48px;height:48px;object-fit:cover;border-radius:6px;" />`;
const realPage = [
  hero("2025/09/Hot-Dog-with-Tater-Tots-1.png", "Hot Dog with Tater Tots"),
  thumb("2025/08/Hot-Dog-Bun-150x150.png", "Hot Dog Bun"),
  hero("2025/09/Images-for-Website-17.png", "Chili &amp; Cheese Hot Dog with Tater Tots"),
  hero("2025/09/Beef-and-Cheese-Nachos.png", "Beef and Cheese Nachos"),
  thumb("2026/08/Doritos-Nacho-Cheese-150x150.png", "Doritos Nacho Cheese"),
  hero("2025/09/Cheese-Nachos.png", "Cheese Nachos"),
  hero("2026/08/Chicken-Tenders-3.png", "Chicken Strips"),
  thumb("2026/07/Waffles-WG-150x150.png", "Waffles WG"),
  hero("2025/09/Teriyaki-Chicken-with-Brown-Rice.png", "Teriyaki Chicken with Rice and Egg Roll"),
  hero("2025/09/Cheese-or-Pepperoni-Pizza-Rippers.png", "Cheese or Pepperoni Pizza Rippers"),
  hero("2025/10/Thanksgiving-Holiday-Meal-1-1024x1024.png", "Thanksgiving Holiday Meal"),
].join("\n");

describe("the district page as it is", () => {
  const real = menuPhotos(realPage, PAGE);

  it("uses only the meals' hero images, as served", () => {
    expect(real.map((p) => p.url)).not.toContain(`${UP}/2026/07/Waffles-WG.png`);
    expect(real.map((p) => p.url)).toContain(`${UP}/2025/10/Thanksgiving-Holiday-Meal-1-1024x1024.png`);
  });

  it("finds this week's lunches", () => {
    const pick = (menu: string) => matchPhoto(menu, real)?.replace(`${UP}/`, "");
    expect(pick("Hot Dog or Chili Dog with Tater Tots")).toBe("2025/09/Images-for-Website-17.png");
    expect(pick("Beef & Cheese Nachos")).toBe("2025/09/Beef-and-Cheese-Nachos.png");
    expect(pick("Chicken and Waffles")).toBeUndefined();
    expect(pick("Teriyaki Chicken w/ Rice and Egg Roll + scratch-made dessert")).toBe("2025/09/Teriyaki-Chicken-with-Brown-Rice.png");
    expect(pick("Cheese or Pepperoni Pizza Rippers")).toBe("2025/09/Cheese-or-Pepperoni-Pizza-Rippers.png");
  });
});

describe("addLunchPhotos", () => {
  const days = [
    { day: "Mon", date: "2026-11-09", menu: "Hot Dog or Chili Dog with Tater Tots" },
    { day: "Tue", date: "2026-11-10", menu: "Beef & Cheese Nachos", photo: "https://example.com/zo.jpg" },
    { day: "Wed", date: "2026-11-11", menu: "Chicken and Waffles" },
  ];

  it("fills the days that have no photo and keeps the ones Zo sent", async () => {
    const fake = (async () => new Response(html)) as unknown as typeof fetch;
    const out = await addLunchPhotos(days, PAGE, fake);
    expect(out.map((d) => d.photo)).toEqual([`${UP}/2025/09/Images-for-Website-17.png`, "https://example.com/zo.jpg", undefined]);
  });

  it("returns the days unchanged when the page fails", async () => {
    const down = (async () => { throw new Error("offline"); }) as unknown as typeof fetch;
    expect(await addLunchPhotos(days, PAGE, down)).toEqual(days);
    const missing = (async () => new Response("", { status: 404 })) as unknown as typeof fetch;
    expect(await addLunchPhotos(days, PAGE, missing)).toEqual(days);
  });
});
