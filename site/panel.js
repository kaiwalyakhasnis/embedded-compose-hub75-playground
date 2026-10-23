// An LED panel on a canvas: each pixel of the frame is one LED, drawn as a
// dot on a black board with a little glow. Off LEDs stay faintly visible,
// as on a real panel.

export class Panel {
  constructor(canvas, width, height) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.w = width;
    this.h = height;
    this.frames = 0;
    this.pixels = document.createElement("canvas");  // the frame, one pixel per LED
    this.pixels.width = width;
    this.pixels.height = height;
    this.pctx = this.pixels.getContext("2d");
    this.image = this.pctx.createImageData(width, height);
    this.image.data.fill(255);  // alpha; the colours come with each frame
    for (let i = 0; i < width * height; i++) this.image.data.set([0, 0, 0], i * 4);
    this.pctx.putImageData(this.image, 0, 0);
    this.pending = false;
    new ResizeObserver(() => this.layout()).observe(canvas.parentElement);
    this.layout();
  }

  /** A frame from matrix.cpp: width * height RGB triples. */
  frame(rgb) {
    const d = this.image.data;
    for (let i = 0, j = 0; i < rgb.length; i += 3, j += 4) {
      d[j] = rgb[i];
      d[j + 1] = rgb[i + 1];
      d[j + 2] = rgb[i + 2];
    }
    this.pctx.putImageData(this.image, 0, 0);
    this.frames++;
    this.request();
  }

  /** The LED under a point of the canvas (CSS pixels), or null. */
  ledAt(cssX, cssY) {
    const x = Math.floor((cssX * this.dpr - this.ox) / this.pitch);
    const y = Math.floor((cssY * this.dpr - this.oy) / this.pitch);
    return { x: Math.max(0, Math.min(this.w - 1, x)), y: Math.max(0, Math.min(this.h - 1, y)) };
  }

  layout() {
    const box = this.canvas.parentElement.getBoundingClientRect();
    this.dpr = window.devicePixelRatio || 1;
    const cw = Math.max(1, Math.floor(box.width * this.dpr));
    const ch = Math.max(1, Math.floor(box.height * this.dpr));
    this.pitch = Math.max(2, Math.floor(Math.min(cw / this.w, ch / this.h)));
    this.canvas.width = cw;
    this.canvas.height = ch;
    this.canvas.style.width = box.width + "px";
    this.canvas.style.height = box.height + "px";
    this.ox = Math.floor((cw - this.pitch * this.w) / 2);
    this.oy = Math.floor((ch - this.pitch * this.h) / 2);
    this.mask = this.makeMask();
    this.request();
  }

  // Black everywhere but a round dot per LED.
  makeMask() {
    const p = this.pitch;
    const cell = document.createElement("canvas");
    cell.width = cell.height = p;
    const c = cell.getContext("2d");
    c.fillStyle = "#000";
    c.fillRect(0, 0, p, p);
    c.globalCompositeOperation = "destination-out";
    c.beginPath();
    c.arc(p / 2, p / 2, p * 0.38, 0, Math.PI * 2);
    c.fill();
    return this.ctx.createPattern(cell, "repeat");
  }

  request() {
    if (this.pending) return;
    this.pending = true;
    requestAnimationFrame(() => {
      this.pending = false;
      this.draw();
    });
  }

  draw() {
    const { ctx, pitch: p, ox, oy, w, h } = this;
    const pw = p * w, ph = p * h;
    ctx.save();
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "#050506";
    ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(this.pixels, ox, oy, pw, ph);
    // Unlit LEDs: dark grey dots.
    ctx.globalCompositeOperation = "lighten";
    ctx.fillStyle = "#17171a";
    ctx.fillRect(ox, oy, pw, ph);
    // Gaps between LEDs.
    ctx.globalCompositeOperation = "source-over";
    ctx.translate(ox, oy);
    ctx.fillStyle = this.mask;
    ctx.fillRect(0, 0, pw, ph);
    ctx.translate(-ox, -oy);
    // Glow.
    if (p >= 4) {
      ctx.globalCompositeOperation = "lighter";
      ctx.globalAlpha = 0.45;
      ctx.imageSmoothingEnabled = true;
      ctx.filter = `blur(${Math.round(p * 0.7)}px)`;
      ctx.drawImage(this.pixels, ox, oy, pw, ph);
    }
    ctx.restore();
  }

  /** The frame as a PNG of the panel as shown. */
  snapshot() {
    return this.canvas.toDataURL("image/png");
  }
}
