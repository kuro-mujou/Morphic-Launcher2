package inkspire.morphic.feature.settings.about

/**
 * A bullet: an optional bold lead-in, then the sentence that follows it.
 *
 * **The lead-in is a field rather than markup**, which is what lets this whole document be plain strings. A policy
 * wants emphasis in about four places; carrying inline markup for that would mean a parser in the app and a second
 * one wherever else the document is rendered, and two parsers that must agree is the fault this codebase keeps
 * un-making. A structured lead-in covers every case the text actually has.
 */
internal data class PolicyBullet(val term: String?, val text: String)

/** A run of the policy. Deliberately few shapes: a policy is headings, prose and lists, and nothing else. */
internal sealed interface PolicyBlock {
    /** A top-level section heading. */
    data class Heading(val text: String) : PolicyBlock

    /** A heading *inside* a section — the "what the app accesses" entries are all of these. */
    data class Subheading(val text: String) : PolicyBlock

    data class Paragraph(val text: String) : PolicyBlock

    data class Bullets(val items: List<PolicyBullet>) : PolicyBlock

    /**
     * An address to write to.
     *
     * **Its own shape rather than a paragraph ending in an email address**, because both renderings want to *do*
     * something with it and neither can if it arrives as prose: the page makes it a `mailto:` link and the pane opens
     * a mail app. Written as text it is a string a reader has to retype, which for a privacy contact is the
     * difference between being written to and not.
     */
    data class Contact(val text: String, val email: String) : PolicyBlock
}

/**
 * The privacy policy — **the one copy of it**, and the source both renderings come from.
 *
 * Two things display this text: the in-app pane ([PrivacyPolicyDetail]) and `privacy-policy.html` at the repo root,
 * which is the URL Play Console is given. Those are two renderers of one document, which this codebase's standing
 * rule says must share a derivation rather than an intention — and a policy is the worst possible thing to keep in
 * step by hand, because the failure is invisible from inside either one. So the document lives here as data, the pane
 * draws it, and `PrivacyPolicyHtmlTest` regenerates the HTML from it and fails the build when the committed file has
 * fallen behind.
 *
 * @property effective the date the current wording took effect, written out. A string rather than a date: it is
 *   editorial — it changes when the *policy* changes, not when the file is touched — so deriving it from anything
 *   would make it move on its own.
 * @property lede the claim the whole document rests on, shown first and set apart in both renderings.
 */
internal data class PrivacyPolicyDocument(
    val title: String,
    val effective: String,
    val lede: String,
    val blocks: List<PolicyBlock>,
)

internal val PrivacyPolicy = PrivacyPolicyDocument(
    title = "Privacy Policy",
    effective = "12 September 2026",
    lede = "Morphic Launcher collects nothing and sends nothing. It has no account, no ads, no analytics and no " +
        "crash reporting. It does not request the Android INTERNET permission, so it cannot transmit data anywhere " +
        "even in principle — the operating system enforces this, and you can check it yourself on the About screen.",
    blocks = listOf(
        PolicyBlock.Paragraph(
            "Everything below describes what the app reads on your device, and why. None of it leaves your device.",
        ),

        PolicyBlock.Heading("What the app accesses on your device"),

        PolicyBlock.Subheading("Your installed apps"),
        PolicyBlock.Paragraph(
            "A launcher's job is to show and start your apps, so it reads the list of apps installed on the device " +
                "along with their names and icons. This is used to draw your home screen and app list, and to " +
                "launch what you tap. The list is never recorded off-device.",
        ),

        PolicyBlock.Subheading("Your layout and settings"),
        PolicyBlock.Paragraph(
            "Where you put each icon, your folders, your icon designs, your wallpaper choice and every preference " +
                "you set are stored in the app's own private storage on the device. Uninstalling the app deletes " +
                "all of it.",
        ),

        PolicyBlock.Subheading("Images you choose"),
        PolicyBlock.Paragraph(
            "With your permission to read images (READ_MEDIA_IMAGES, or READ_EXTERNAL_STORAGE on Android 12 and " +
                "older), the app can use a picture you pick as a wallpaper. There is one other, narrower use: the " +
                "wallpaper capture feature has no way to take a screenshot for you, so it asks you to take one and " +
                "then watches for the next image to appear in your gallery, using only that one. It does not scan, " +
                "index, upload or otherwise read your photo library.",
        ),

        PolicyBlock.Subheading("Setting your wallpaper"),
        PolicyBlock.Paragraph(
            "SET_WALLPAPER lets the app apply a wallpaper you have chosen. It is used for nothing else.",
        ),

        PolicyBlock.Subheading("Uninstalling apps"),
        PolicyBlock.Paragraph(
            "REQUEST_DELETE_PACKAGES lets the app ask Android to uninstall an app when you choose to remove one " +
                "from your home screen. Android shows its own confirmation, and the app cannot remove anything " +
                "without it.",
        ),

        PolicyBlock.Heading("Two things handled by others, not by us"),
        PolicyBlock.Bullets(
            listOf(
                PolicyBullet(
                    term = "Widgets.",
                    text = "The launcher can host widgets belonging to other apps. Those widgets are the other " +
                        "app's code, running under that app's own privacy policy — not this one.",
                ),
                PolicyBullet(
                    term = "Android's own backup.",
                    text = "If device backup is enabled in your Android settings, the app's settings and layout may " +
                        "be included in your device's backup to your own Google account. That backup is performed " +
                        "and controlled by Android and Google, not by this app, and is governed by Google's privacy " +
                        "policy. You can exclude it by turning device backup off in your Android settings.",
                ),
            ),
        ),

        PolicyBlock.Heading("Data we collect"),
        PolicyBlock.Paragraph(
            "None. There is no server, no account, no identifier, no telemetry and no third-party SDK that collects " +
                "anything. Nothing is shared or sold, because nothing is gathered in the first place.",
        ),

        PolicyBlock.Heading("Children"),
        PolicyBlock.Paragraph(
            "The app is not directed at children and collects no personal information from anyone, of any age.",
        ),

        PolicyBlock.Heading("Changes to this policy"),
        PolicyBlock.Paragraph(
            "If the app ever gains a feature that changes any of the above, this policy will be updated and its " +
                "effective date changed before that version is released.",
        ),

        PolicyBlock.Heading("Contact"),
        PolicyBlock.Contact(text = "Questions about this policy:", email = "mguyenmanhtuan@gmail.com"),
    ),
)
