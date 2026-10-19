// Draws the launcher icon's foreground: the cucumber slice on transparency,
// with the soft shadow of something lying on the ground. The ground itself,
// the site's lavender and grid, is ic_launcher_background.xml, so the two
// layers make the same icon the iOS and desktop apps have.
//
//   swift scripts/launcher-icon/make_launcher_icon.swift   # needs macOS
//
// Writes drawable-nodpi/ic_launcher_foreground.png, by day and by night
// alike: Android draws no ground of its own behind an adaptive icon, as iOS
// does for a dark one, and the site's night grid looked heavy. The layer is
// 108dp, 432px here, of which a launcher shows the middle 72dp; the slice is
// drawn 61.2dp across (85% of what shows), and its own padding leaves it
// about 60% across, inside the 66dp circle every mask keeps. Smaller than on
// iOS: Android's masks crop harder.
//
// slice.png beside this script is the 1024px slice on transparency, padding
// and all, as in oeee-cafe-apple's scripts/app-icon/slice.png.
import AppKit
import CoreGraphics

let here = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let res = here.appendingPathComponent("../../app/src/main/res").standardizedFileURL

let size = 432.0
let markSize = size * 72 / 108 * 0.85

func color(_ hex: UInt32, _ alpha: Double) -> CGColor {
    CGColor(red: Double((hex >> 16) & 0xff) / 255, green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255, alpha: alpha)
}

let sliceSource = CGImageSourceCreateWithURL(here.appendingPathComponent("slice.png") as CFURL, nil)!
let slice = CGImageSourceCreateImageAtIndex(sliceSource, 0, nil)!
let space = CGColorSpace(name: CGColorSpace.sRGB)!

do {
    let ctx = CGContext(data: nil, width: Int(size), height: Int(size), bitsPerComponent: 8,
                        bytesPerRow: 0, space: space,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.setShadow(offset: CGSize(width: 0, height: -markSize * 0.024),
                  blur: markSize * 0.048, color: color(0x1c2811, 0.34))
    ctx.interpolationQuality = .high
    ctx.draw(slice, in: CGRect(x: (size - markSize) / 2, y: (size - markSize) / 2,
                               width: markSize, height: markSize))
    let dir = res.appendingPathComponent("drawable-nodpi")
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    let url = dir.appendingPathComponent("ic_launcher_foreground.png")
    let dest = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
    guard CGImageDestinationFinalize(dest) else { fatalError("Couldn't write \(url.path)") }
    print("wrote \(url.path)")
}
