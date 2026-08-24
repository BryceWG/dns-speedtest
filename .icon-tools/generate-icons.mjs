import sharp from 'sharp'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const svgPath = path.join(root, 'docs', 'images', 'icon.svg')
const resDir = path.join(root, 'app', 'src', 'main', 'res')

const ICON_SCALE = 0.6

const legacySizes = {
  'mipmap-mdpi': 48,
  'mipmap-hdpi': 72,
  'mipmap-xhdpi': 96,
  'mipmap-xxhdpi': 144,
  'mipmap-xxxhdpi': 192,
}

const foregroundSizes = {
  'drawable-mdpi': 108,
  'drawable-hdpi': 162,
  'drawable-xhdpi': 216,
  'drawable-xxhdpi': 324,
  'drawable-xxxhdpi': 432,
}

const transparent = { r: 0, g: 0, b: 0, alpha: 0 }
const backgroundColor = { r: 255, g: 255, b: 255, alpha: 255 }

async function renderIcon(size, background = null) {
  const iconSize = Math.round(size * ICON_SCALE)
  const offset = Math.round((size - iconSize) / 2)
  const foreground = await sharp(svgPath, { density: Math.max(192, iconSize * 2) })
    .resize(iconSize, iconSize, {
      fit: 'contain',
      background: transparent,
    })
    .png()
    .toBuffer()

  return sharp({
    create: {
      width: size,
      height: size,
      channels: 4,
      background: background ?? transparent,
    },
  })
    .composite([{ input: foreground, top: offset, left: offset }])
    .png()
    .toBuffer()
}

async function writePng(folder, name, size, background = null) {
  const dir = path.join(resDir, folder)
  await mkdir(dir, { recursive: true })
  const buffer = await renderIcon(size, background)
  await writeFile(path.join(dir, `${name}.png`), buffer)
  console.log(`wrote ${folder}/${name}.png (${size}px, icon ${Math.round(size * ICON_SCALE)}px)`)
}

for (const [folder, size] of Object.entries(legacySizes)) {
  await writePng(folder, 'ic_launcher', size, backgroundColor)
  await writePng(folder, 'ic_launcher_round', size, backgroundColor)
}

for (const [folder, size] of Object.entries(foregroundSizes)) {
  await writePng(folder, 'ic_launcher_foreground', size, null)
}

console.log('done')
