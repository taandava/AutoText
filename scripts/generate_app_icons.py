import math
import os
from PIL import Image, ImageDraw

def render_emblem(size, is_round=False):
    # Render at 10x scale for perfect anti-aliasing via Lanczos resampling
    scale = 10
    hires_size = size * scale
    img = Image.new("RGBA", (hires_size, hires_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx = hires_size / 2.0
    cy = hires_size / 2.0

    # Background: sleek dark circle/squircle
    bg_radius = (hires_size / 2.0) - (2 * scale)
    if is_round:
        draw.ellipse(
            [(cx - bg_radius, cy - bg_radius), (cx + bg_radius, cy + bg_radius)],
            fill=(26, 26, 26, 255),  # Deep charcoal #1A1A1A
            outline=(255, 214, 0, 180),  # Subtle gold outline
            width=int(1.5 * scale)
        )
    else:
        # Rounded rectangle
        corner = int(18 * scale * (size / 108.0))
        draw.rounded_rectangle(
            [(cx - bg_radius, cy - bg_radius), (cx + bg_radius, cy + bg_radius)],
            radius=corner,
            fill=(26, 26, 26, 255),
            outline=(255, 214, 0, 180),
            width=int(1.5 * scale)
        )

    # Geometry scaled to viewport (108 units)
    unit = hires_size / 108.0

    r_outer = 32.0 * unit
    r_inner = 11.0 * unit
    r_hub = 7.0 * unit
    r_core = 2.5 * unit

    # 3 Blades centered at:
    # Blade 1: Top (90 deg up -> math angle 270 deg or -90 deg, spanning from 240 deg to 300 deg)
    # Blade 2: Bottom Right (spanning 0 deg to 60 deg)
    # Blade 3: Bottom Left (spanning 120 deg to 180 deg)
    blade_angles = [
        (240, 300),
        (0, 60),
        (120, 180)
    ]

    # 1. Outer Glow Layer (#80FF9100 - translucent amber glow)
    glow_color = (255, 145, 0, 100)
    glow_r_outer = r_outer + (1.5 * unit)
    glow_r_inner = r_inner - (1.0 * unit)

    for start_deg, end_deg in blade_angles:
        pts = []
        # Outer arc
        steps = 30
        for i in range(steps + 1):
            deg = start_deg + (end_deg - start_deg) * (i / steps)
            rad = math.radians(deg)
            x = cx + glow_r_outer * math.cos(rad)
            y = cy + glow_r_outer * math.sin(rad)
            pts.append((x, y))
        # Inner arc in reverse
        for i in range(steps, -1, -1):
            deg = start_deg + (end_deg - start_deg) * (i / steps)
            rad = math.radians(deg)
            x = cx + glow_r_inner * math.cos(rad)
            y = cy + glow_r_inner * math.sin(rad)
            pts.append((x, y))
        draw.polygon(pts, fill=glow_color)

    # Glow around hub
    draw.ellipse(
        [(cx - (r_hub + 1.5 * unit), cy - (r_hub + 1.5 * unit)),
         (cx + (r_hub + 1.5 * unit), cy + (r_hub + 1.5 * unit))],
        fill=glow_color
    )

    # 2. Main Solid Gold Blades (#FFD600)
    gold_color = (255, 214, 0, 255)
    for start_deg, end_deg in blade_angles:
        pts = []
        steps = 40
        for i in range(steps + 1):
            deg = start_deg + (end_deg - start_deg) * (i / steps)
            rad = math.radians(deg)
            x = cx + r_outer * math.cos(rad)
            y = cy + r_outer * math.sin(rad)
            pts.append((x, y))
        for i in range(steps, -1, -1):
            deg = start_deg + (end_deg - start_deg) * (i / steps)
            rad = math.radians(deg)
            x = cx + r_inner * math.cos(rad)
            y = cy + r_inner * math.sin(rad)
            pts.append((x, y))
        draw.polygon(pts, fill=gold_color)

    # 3. Center Hub Circle (#FFD600)
    draw.ellipse(
        [(cx - r_hub, cy - r_hub), (cx + r_hub, cy + r_hub)],
        fill=gold_color
    )

    # 4. Center Core Point (#212121 - Dark core matching background)
    core_color = (33, 33, 33, 255)
    draw.ellipse(
        [(cx - r_core, cy - r_core), (cx + r_core, cy + r_core)],
        fill=core_color
    )

    # Downsample using Lanczos
    return img.resize((size, size), Image.Resampling.LANCZOS)

def main():
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }

    base_res = os.path.join("app", "src", "main", "res")

    for folder, size in densities.items():
        dir_path = os.path.join(base_res, folder)
        os.makedirs(dir_path, exist_ok=True)

        # Standard squircle launcher icon
        img = render_emblem(size, is_round=False)
        img.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        img.save(os.path.join(dir_path, "ic_super_emblem.png"), "PNG")

        # Round launcher icon
        img_round = render_emblem(size, is_round=True)
        img_round.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")
        img_round.save(os.path.join(dir_path, "ic_super_emblem_round.png"), "PNG")
        print(f"Generated {folder} ({size}x{size})")

    # Also generate high-res 512x512 play store / web icon and 192x192 notification large icon
    web_dir = os.path.join(base_res, "drawable")
    img_512 = render_emblem(512, is_round=False)
    img_512.save(os.path.join(web_dir, "ic_launcher_web.png"), "PNG")
    print("Generated 512x512 web icon")

    img_large = render_emblem(192, is_round=True)
    img_large.save(os.path.join(web_dir, "ic_launcher_large.png"), "PNG")
    print("Generated 192x192 notification large icon")

if __name__ == "__main__":
    main()
