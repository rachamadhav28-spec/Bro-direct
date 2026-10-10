package com.bro.assistant.actions

import android.content.Context
import android.content.Intent

/**
 * What BRO knows about the phone and about each app: how the app is laid out, what it can do,
 * the fastest route for common jobs, and the traps. The agent gets the phone-wide rules every
 * time, plus the playbooks that match the app on screen and the words in the user's goal.
 * Playbooks are guidance, not truth: the agent still checks the real screen every step.
 */
object AppKnowledge {

    class Playbook(val name: String, val packages: List<String>, val keywords: List<String>, val guide: String)

    /** Rules that hold on the whole phone (iQOO / Funtouch OS / stock Android). */
    val PHONE = """
        HOW THIS PHONE WORKS
        - Android with the iQOO Funtouch/OriginOS skin. Home screen = app icons + dock; swipe up from the bottom for
          the app drawer (search box at top finds any app); the open_app action is faster than hunting for icons.
        - Notification shade: swipe down from top (action "notifications"). Quick Settings tiles are in the shade
          (swipe down again). "recents" shows running apps. "back" goes one screen back; "home" leaves the app.
        - Most apps: bottom tab bar = main sections; top-right three dots (More options) = extra menu; a magnifier
          icon = search; a floating round "+" button = create something new; long press = select / extra options.
        - Lists load more when you scroll. If the thing you need is not in SCREEN ELEMENTS, scroll down (or up)
          and look again before giving up; also try the search icon of the app.
        - Text fields: "type" with the element number. After typing a search, use submit:true or tap the search/go key.
        - Dialogs: permission pop-ups ("Allow", "While using the app", "Allow all the time", "Deny"), "Update
          available"/"Rate us"/"Sign in" nags ("Not now", "Skip", "Later", "Close", X) can be dismissed when they
          block the goal. NEVER allow permissions the goal does not need and NEVER accept terms that spend money.
        - Websites: use open_url with the full https address; it is far more reliable than typing into the browser bar.
        - Loading spinners: use "wait" once or twice before deciding something is broken.
        - Login screens, OTP, passwords, CAPTCHAs, face/fingerprint prompts: stop and ask the user to do that part.
        - Money apps (UPI, banks, shopping checkout), delete/uninstall/format, and sending to other people are
          irreversible: do the preparation, then let the confirmation prompt happen before the final tap.
    """.trimIndent()

    private val books = listOf(
        Playbook("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp", "wa ", "chat", "status"),
            """
            WhatsApp: tabs along the top/bottom = Chats, Updates (Status), Communities, Calls. Green round button =
            new chat. Magnifier = search chats by name. Inside a chat: bottom text box "Message" (type here), Send arrow
            appears only after typing (a mic icon shows when empty), paperclip/"+" = attach (Document, Camera, Gallery,
            Audio, Location, Contact), top-right three dots = More (Search, Media, Mute, Clear chat). Tap contact
            name at top for profile/media. To message: open_app WhatsApp -> tap search -> type name -> tap the chat ->
            type in "Message" -> tap Send. Send only when the user gave the exact text. Voice call/video call icons
            are in the chat top bar. Unread chats show a green number. Long-press a chat to pin/archive/mute.
            """.trimIndent()),
        Playbook("Instagram", listOf("com.instagram.android"), listOf("instagram", "insta", "reel", "story", "dm"),
            """
            Instagram: bottom bar = Home feed, Reels, Create (+), Search/Explore, Profile. Paper-plane icon top-right
            (or Messages) = DMs. Heart icon = like (double-tap a post also likes). Speech bubble = comments. Paper
            plane under a post = share. Search tab: type in the search box, results tabs: Top, Accounts, Audio, Tags,
            Places. Stories are the circles at the top of Home. Profile tab shows posts, Edit profile, menu (three
            lines top-right) -> Settings, Saved, Archive. Never post, comment, follow or DM without the user's exact
            words/intent.
            """.trimIndent()),
        Playbook("YouTube", listOf("com.google.android.youtube", "app.revanced.android.youtube"), listOf("youtube", "video", "song", "channel", "subscribe", "play"),
            """
            YouTube: bottom bar = Home, Shorts, Create (+), Subscriptions, You (Library). Magnifier top-right = search;
            type the query and submit. Results are a vertical list; tap a video thumbnail/title to play. Player: tap the
            video once to show controls (play/pause, seek bar, captions CC, settings gear for quality/speed, full-screen
            corner icon); like/dislike/share/download/save sit under the title; "Subscribe" button under the channel
            name. Ads: "Skip" / "Skip ad" appears after ~5 s. "You" tab has History, Playlists, Watch later, Downloads.
            """.trimIndent()),
        Playbook("Chrome / web browsing", listOf("com.android.chrome", "com.brave.browser", "org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.vivo.browser", "com.google.android.apps.chrome"),
            listOf("chrome", "browser", "website", "web", "google", "search", "http", "www.", ".com", "github.com", "open url", "internet"),
            """
            Chrome: address bar at top ("Search or type web address") - tap, type a search or URL, submit. Tab switcher =
            small square with a number (top-right); three dots = menu (New tab, New Incognito tab, History, Downloads,
            Bookmarks, Share, Find in page, Desktop site, Settings). Back arrow/system back goes to previous page. Pages
            are long: scroll to find content; links are elements marked {tap}. Cookie/consent banners: tap "Accept" or
            "Reject all" only if they block reading. Search results: Google shows ads first (marked "Sponsored" - avoid),
            then organic links; "People also ask" boxes expand on tap. To read a page, scroll and read the visible
            text; summarize in the final "done" summary. Prefer the open_url action with the exact https address
            for known sites. Downloads appear in the notification shade and Menu -> Downloads. Never save passwords
            or sign in to accounts unless the user logs in themselves.
            """.trimIndent()),
        Playbook("GitHub (app and github.com)", listOf("com.github.android"), listOf("github", "repo", "repository", "commit", "pull request", "issue", "actions tab", "workflow", "git"),
            """
            GitHub. IMPORTANT: BRO already has a direct GitHub connection (token in Settings) that handles repos, files,
            issues, pull requests, merges, pushing code and build status faster and more safely than tapping. Prefer
            that; use the screen only to look at things the API does not cover (profile settings, code search results,
            reading a page, notifications, stars, forks). On github.com (open_url): github.com/<user> = profile,
            github.com/<user>/<repo> = repo (tabs: Code, Issues, Pull requests, Actions, Projects, Wiki, Security,
            Settings), /actions = workflow runs (green tick = success, red cross = failed, yellow dot = running; open a
            run to see "Artifacts" at the bottom for the APK), /issues, /pulls, /commits, /releases, github.com/search?q=
            <words>, github.com/settings/tokens for tokens. Green "Code" button -> Download ZIP / clone URL. Pencil
            icon edits a file; "Commit changes" saves it. GitHub mobile app: bottom tabs Home, Notifications, Explore,
            Profile; Repositories under Profile. Never delete repos, never change visibility, never create tokens.
            """.trimIndent()),
        Playbook("Gmail / Email", listOf("com.google.android.gm", "com.microsoft.office.outlook"), listOf("gmail", "email", "mail", "inbox", "compose"),
            """
            Gmail: search bar at top ("Search in mail"); hamburger menu (three lines) = labels (Inbox, Starred,
            Sent, Drafts, Spam, Trash, All mail). "Compose" pill button bottom-right opens: To, Subject, body, Send
            paper-plane top-right, attach paperclip top. Open an email by tapping its row; icons on top: Archive, Delete,
            Mark unread, More. Reply arrow at the bottom of a message. Unread = bold sender. Never send an email unless
            the user gave recipient and content; sending needs the user's OK.
            """.trimIndent()),
        Playbook("Google Maps", listOf("com.google.android.apps.maps"), listOf("maps", "map", "navigate", "direction", "route", "nearby", "restaurant", "location", "traffic"),
            """
            Maps: search box at top ("Search here"); chips below for Restaurants/Petrol/ATMs. After choosing a place the
            bottom sheet shows name, rating, Directions (blue), Start, Call, Save, Share, Photos, Reviews, hours. Directions:
            set From ("Your location") and To, choose mode icons (car, transit, walk, bike); "Start" begins turn-by-turn
            navigation. Time and distance are in the bottom sheet. "Explore"/"Go"/"Saved"/"Updates" tabs at the bottom.
            """.trimIndent()),
        Playbook("Telegram", listOf("org.telegram.messenger", "org.thunderdog.challegram"), listOf("telegram"),
            """
            Telegram: hamburger (three lines, top-left) = menu (Contacts, Saved Messages, Settings). Magnifier top-right
            = search chats/people. Pencil round button = new message. In a chat: bottom field "Message", Send arrow
            appears after typing, paperclip = attach, mic = voice note. Saved Messages is the user's private notes chat.
            """.trimIndent()),
        Playbook("Settings", listOf("com.android.settings", "com.vivo.settings", "com.bbk.launcher2"), listOf("settings", "setting", "wifi", "bluetooth", "brightness", "display", "battery", "storage", "wallpaper", "permission", "apps", "update", "sound", "volume", "notification", "network", "language", "date", "time zone", "accessibility", "default app"),
            """
            Settings (Funtouch/OriginOS): search box at the top finds any setting - use it ("type" the setting name,
            tap the matching result). Main groups: Network (Wi-Fi, Mobile network, Hotspot, VPN, Airplane), Bluetooth &
            devices, Display & brightness (dark mode, font size, refresh rate, eye protection), Sound & vibration,
            Notifications & status bar, Wallpaper & themes, Battery (power saving), Storage, Apps & permissions (App
            management -> pick an app -> Permissions, Notifications, Storage & cache, Force stop, Uninstall), Privacy &
            security, Accessibility, System update, About phone. Switches are elements marked {checked}/{unchecked}:
            tap once to flip, then verify. Never factory-reset, erase data, remove accounts, or turn off the Accessibility
            service BRO needs.
            """.trimIndent()),
        Playbook("Phone / Dialer & Contacts", listOf("com.android.dialer", "com.google.android.dialer", "com.vivo.contacts", "com.android.contacts", "com.google.android.contacts"), listOf("call", "dial", "contact", "phone", "recent calls", "missed"),
            """
            Phone: tabs Recents/Favorites/Contacts/Keypad (names vary); search field at top for contacts; green phone
            button dials. Keypad: tap digits; tap the green call button. Recents shows missed (red) and answered calls.
            Contact page: tap name for Call, Message, Video, Edit (pencil), More (Share, Delete, Block). Never delete or
            block contacts unless asked. Calling a number is an action the user must have asked for explicitly.
            """.trimIndent()),
        Playbook("Messages (SMS)", listOf("com.google.android.apps.messaging", "com.android.mms", "com.vivo.message"), listOf("sms", "text message", "messages", "otp"),
            """
            Messages: conversation list sorted by recent; "Start chat" button for a new one, search at top. In a chat the
            field says "Text message"/"Message"; Send arrow appears after typing. OTP codes arrive here - READ them to
            the user only if asked; never type OTPs into other apps.
            """.trimIndent()),
        Playbook("Camera", listOf("com.android.camera", "com.vivo.camera", "com.google.android.GoogleCamera"), listOf("camera", "photo", "selfie", "picture", "video record", "take a photo"),
            """
            Camera: big round shutter button at the bottom centre takes a photo; mode strip above it (Photo, Video,
            Portrait, Night, Pro, More). Small circle bottom-left = last photo/gallery; circular arrows = flip front/back
            camera; lightning = flash; timer icon. In Video mode the same big button starts/stops recording (red).
            """.trimIndent()),
        Playbook("Gallery / Google Photos", listOf("com.google.android.apps.photos", "com.vivo.gallery", "com.android.gallery3d"), listOf("gallery", "photos", "photo", "album", "picture", "screenshot", "image"),
            """
            Photos/Gallery: grid of pictures newest first; tabs Photos, Albums/Collections, Search. Tap a picture to open;
            bottom bar: Share, Edit, Favorite (heart/star), Delete (trash), More. Long-press a picture to start
            selecting several. Albums has Screenshots, Camera, Downloads. Delete = irreversible; ask first.
            """.trimIndent()),
        Playbook("Clock / Alarm / Timer", listOf("com.android.deskclock", "com.google.android.deskclock", "com.vivo.alarmclock"), listOf("alarm", "timer", "stopwatch", "clock", "wake me"),
            """
            Clock: tabs Alarm, World clock, Stopwatch, Timer. Alarm tab: "+" adds an alarm (scroll the hour/minute
            wheels or tap the digits and type, AM/PM toggle, repeat days, then OK/Save); each alarm has an on/off
            switch. Timer tab: type digits on the keypad then tap the play/start button.
            """.trimIndent()),
        Playbook("Calendar", listOf("com.google.android.calendar", "com.android.calendar", "com.vivo.calendar"), listOf("calendar", "event", "meeting", "schedule", "reminder", "holiday"),
            """
            Calendar: month/week/day views via the menu or top dropdown; "+" creates an event (title, date, time, all-day
            toggle, location, notifications, Save). Tap an event for details. Holidays appear from the Holidays calendar
            in the menu. Never invent dates; read them from the screen.
            """.trimIndent()),
        Playbook("Files / File manager", listOf("com.google.android.apps.nbu.files", "com.android.documentsui", "com.vivo.filemanager", "com.android.filemanager"), listOf("files", "file manager", "download", "folder", "document", "pdf", "apk", "storage"),
            """
            Files: Browse tab lists Downloads, Images, Videos, Audio, Documents, APKs and Internal storage. Tap a file to
            open; long-press to select, then use Share/Move/Copy/Delete from the top or bottom bar. Installing an APK:
            tap the .apk file -> "Install" (allow "Install unknown apps" for the file manager if asked) -> Open.
            Deleting is irreversible; confirm first.
            """.trimIndent()),
        Playbook("Google Play Store", listOf("com.android.vending"), listOf("play store", "install", "update app", "download app", "uninstall"),
            """
            Play Store: search at top (type app name, submit); tap the right result (check the developer name); green
            "Install" button, then "Open". "Update" for existing apps; profile icon (top-right) -> Manage apps & device
            -> Updates available -> "Update all". Paid apps show a price instead of Install - do NOT buy.
            """.trimIndent()),
        Playbook("Google app / Assistant search", listOf("com.google.android.googlequicksearchbox"), listOf("google search", "search for", "look up"),
            """
            Google app: search bar on top; results as cards/links; tabs All, Images, Videos, News, Shopping. Tap the
            first organic link or a featured answer box. Prefer Chrome with open_url for reading full pages.
            """.trimIndent()),
        Playbook("UPI payments (Google Pay, PhonePe, Paytm)", listOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp"), listOf("pay", "upi", "gpay", "phonepe", "paytm", "money", "bank", "balance", "recharge", "scan"),
            """
            UPI apps: Home shows Pay/Send to contact, Scan QR, Pay phone number, Bank transfer, Recharge & bills,
            transaction history. Paying requires choosing the person, amount, and a UPI PIN. HARD RULES: never type the
            UPI PIN, never press the final Pay/Send/Proceed button without the user's confirmation, never scan or accept
            unknown requests. You may check history, open a person's page, or prepare an amount, then stop and ask.
            """.trimIndent()),
        Playbook("Shopping & food (Amazon, Flipkart, Swiggy, Zomato)", listOf("in.amazon.mShop.android.shopping", "com.flipkart.android", "in.swiggy.android", "com.application.zomato", "com.zepto.app", "com.grofers.customerapp"), listOf("amazon", "flipkart", "swiggy", "zomato", "order", "cart", "buy", "food", "delivery", "grocery", "blinkit", "zepto"),
            """
            Shopping/food apps: search box at top; results list with price and rating; product page has "Add to cart" /
            "Buy now"; cart icon top-right; checkout screens choose address, delivery slot, payment. You may search,
            compare, read prices and reviews, and add to cart. NEVER tap Buy now / Place order / Pay without the
            user's OK (the confirmation prompt covers it) and never choose Cash-on-delivery or change address silently.
            Swiggy/Zomato: pick restaurant -> ADD on items -> View cart -> check bill and address -> Place order.
            """.trimIndent()),
        Playbook("Cab & travel (Uber, Ola, Rapido, IRCTC)", listOf("com.ubercab", "com.olacabs.customer", "com.rapido.passenger", "cris.org.in.prs.ima"), listOf("uber", "ola", "rapido", "cab", "taxi", "ride", "auto", "train", "irctc", "ticket"),
            """
            Cab apps: "Where to?" box -> type destination -> pick suggestion -> choose ride type (price shown) ->
            Confirm/Book. Booking costs money: check price, ask the user before the final Confirm. IRCTC needs login
            and captcha: the user must do those.
            """.trimIndent()),
        Playbook("Spotify / Music", listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.jio.media.jiobeats", "in.startv.hotstar"), listOf("spotify", "music", "song", "playlist", "album", "gaana", "jiosaavn", "listen"),
            """
            Music apps: Search tab (magnifier) -> type song/artist -> tap result -> big Play button. Mini player at the
            bottom: play/pause, next; tap it for the full player (shuffle, repeat, like heart, queue). Spotify free
            plays shuffled for playlists. "Your Library" holds playlists/liked songs.
            """.trimIndent()),
        Playbook("Notes / Docs / Drive", listOf("com.google.android.keep", "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs", "com.vivo.notes", "com.android.notes", "com.microsoft.office.word"), listOf("note", "notes", "keep", "docs", "drive", "document", "write down", "list"),
            """
            Notes/Docs: "+" or "Take a note" button creates a note: first field Title, then body; changes autosave; back
            button leaves the note saved. Drive: tabs Home, Starred, Shared, Files; "+" uploads/creates; three dots on
            a file for Share, Download, Rename, Remove.
            """.trimIndent()),
        Playbook("Calculator", listOf("com.android.calculator2", "com.google.android.calculator", "com.vivo.calculator"), listOf("calculator", "calculate"),
            """
            Calculator: tap digits and operators (+ - × ÷ %), "=" shows the result at the top. "C"/"AC" clears; backspace
            removes one digit. For math, prefer computing it yourself rather than tapping.
            """.trimIndent()),
        Playbook("X / Facebook / LinkedIn / Snapchat", listOf("com.twitter.android", "com.facebook.katana", "com.linkedin.android", "com.snapchat.android"), listOf("twitter", "tweet", "facebook", "linkedin", "snapchat", "post", "follow", "feed"),
            """
            Social apps: bottom bar switches Home/Search/Notifications/Messages; "+" or pen button composes a post. Heart
            = like, speech bubble = reply/comment, arrows = repost/share. Never post, comment, follow, connect or message
            unless the user gave the exact content.
            """.trimIndent())
    )

    /** Playbooks for the app on screen and for words in the goal (best matches first, at most 3). */
    fun relevant(goal: String, currentPackage: String): List<Playbook> {
        val g = goal.lowercase()
        val scored = books.map { b ->
            var score = 0
            if (currentPackage.isNotEmpty() && b.packages.any { it == currentPackage }) score += 5
            for (k in b.keywords) if (g.contains(k)) score += if (k.length > 4) 2 else 1
            b to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        return scored.take(3).map { it.first }
    }

    fun summaryFor(name: String): String? {
        val q = name.trim().lowercase()
        return books.firstOrNull { it.name.lowercase().contains(q) || it.keywords.any { k -> k == q } }?.guide
    }

    private var cachedApps: String? = null

    /** "Name (package)" lines for the launchable apps on this phone. */
    fun installedApps(context: Context): String {
        cachedApps?.let { return it }
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val text = try {
            pm.queryIntentActivities(launcher, 0)
                .map { it.loadLabel(pm).toString() + " (" + it.activityInfo.packageName + ")" }
                .distinct().sorted().take(160).joinToString("; ")
        } catch (e: Exception) { "" }
        cachedApps = text
        return text
    }
}
