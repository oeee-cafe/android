package cafe.oeee.web

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app against the site's own account of what the two say to each other: appContract.json
 * (beside app_bridge.jinja in oeee-cafe/web), whose messages were captured from the page
 * itself. The copy in src/test/resources is fetched again by scripts/sync-app-contract.sh, so
 * a change on the site that this app would mishear fails here rather than on someone's phone.
 */
class AppContractTest {
    private val contract: JSONObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResource("appContract.json")) {
            "No appContract.json in src/test/resources; run scripts/sync-app-contract.sh"
        }.readText()
        JSONObject(text)
    }

    private val messages: JSONObject get() = contract.getJSONObject("messages")

    /**
     * The examples of [type], as the page wrote them: each goes to the app as it came, nulls
     * and all, and is read here as a map to say what it should come to.
     */
    private fun examples(type: String): List<JSONObject> = messages.optJSONArray(type).objects()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun parse(example: JSONObject): BridgeMessage? = BridgeMessage.parse(example.toString())

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    /** A string with something in it; an empty one says nothing, as the app reads words. */
    private fun Map<*, *>.word(key: String): String? = string(key)?.ifEmpty { null }

    private fun Map<*, *>.int(key: String): Int = (this[key] as Number).toInt()

    // -- What the page sends ------------------------------------------------------------------

    /**
     * What each example the app acts on must come to. The rules are the contract's, written
     * out: a field is read as the field it names, and nothing more is asked of it.
     */
    private val expected: Map<String, (Map<*, *>) -> BridgeMessage?> = mapOf(
        "page" to { m ->
            BridgeMessage.Page(signedIn = m["signedIn"] as Boolean?, refreshable = m["refreshable"] as Boolean)
        },
        "theme" to { m ->
            val ground = parseCssColor(m.string("ground"))
            val grid = parseCssColor(m.string("grid"))
            // A colour the page says is one the app can read.
            if (m.string("ground") != null) assertNotNull("ground ${m["ground"]}", ground)
            if (m.string("grid") != null) assertNotNull("grid ${m["grid"]}", grid)
            val choice = m.string("choice")?.takeIf { it == "light" || it == "dark" }
            BridgeMessage.Theme(ground = ground, grid = grid, choice = choice)
        },
        "words" to { m ->
            BridgeMessage.Words(
                leaveTitle = m.word("leaveTitle"),
                leaveBody = m.word("leaveBody"),
                leave = m.word("leave"),
                stay = m.word("stay"),
                saveImage = m.word("saveImage"),
                copyImage = m.word("copyImage"),
                share = m.word("share"),
                copyLink = m.word("copyLink"),
                savedImage = m.word("savedImage"),
                savedFile = m.word("savedFile"),
                saveFailed = m.word("saveFailed")
            )
        },
        "haptic" to { m -> BridgeMessage.Haptic(m.string("name")!!) },
        "pressed" to { m ->
            val drawing = m["drawing"] as Map<*, *>?
            val src = drawing?.string("src")
            // Only a drawing the app can fetch itself; a data: or blob: image is none.
            BridgeMessage.Pressed(
                if (drawing == null || src == null || !(src.startsWith("https://") || src.startsWith("http://"))) {
                    null
                } else {
                    BridgeMessage.PressedDrawing(
                        src = src,
                        link = drawing.string("link")?.ifEmpty { null },
                        width = drawing.int("width"),
                        height = drawing.int("height")
                    )
                }
            )
        },
        // Steam's has no nonce, and is only ever sent to the Steam build.
        "signIn" to { m -> m.string("nonce")?.ifEmpty { null }?.let { BridgeMessage.SignIn(it) } },
        "browse" to { m -> BridgeMessage.Browse(m.string("url")!!) },
        "share" to { m -> BridgeMessage.Share(title = m.string("title") ?: "", text = m.string("text")!!) },
        "download" to { m -> BridgeMessage.Download(name = m.string("name") ?: "", data = m.string("data")!!) },
        "prices" to { m ->
            BridgeMessage.Prices((m["products"] as List<*>).mapNotNull { (it as? String)?.ifEmpty { null } })
        },
        "purchase" to { m -> BridgeMessage.Purchase(m.string("product")!!) },
        "restore" to { _ -> BridgeMessage.Restore }
    )

    @Test
    fun everyMessageTheAppActsOnIsHeardAsThePageMeantIt() {
        for ((type, expect) in expected) {
            val examples = examples(type)
            assertTrue("The contract has no example of $type, which this app acts on", examples.isNotEmpty())
            for (example in examples) {
                assertEquals("$type: $example", expect(example.fields()), parse(example))
            }
        }
    }

    @Test
    fun theMessagesTheAppActsOnMostlyComeToSomething() {
        // The expectations above could all be "nothing" and still agree; these may not be.
        assertTrue(examples("page").all { parse(it) is BridgeMessage.Page })
        assertTrue(examples("words").all { parse(it) is BridgeMessage.Words })
        assertTrue(examples("download").all { parse(it) is BridgeMessage.Download })
        assertTrue(examples("prices").all { (parse(it) as BridgeMessage.Prices).products.isNotEmpty() })
        assertTrue(examples("purchase").all { parse(it) is BridgeMessage.Purchase })
        assertTrue(examples("restore").all { parse(it) == BridgeMessage.Restore })
        assertTrue(
            "Google's sign-in, with its nonce, is one",
            examples("signIn").any { it.opt("provider") == "google" && parse(it) is BridgeMessage.SignIn }
        )
        // A press on a drawing is a drawing the app offers a menu for, with all the page said.
        val onDrawing = examples("pressed").filter { !it.isNull("drawing") }
        assertTrue("The contract has no press on a drawing", onDrawing.isNotEmpty())
        for (example in onDrawing) {
            val said = example.getJSONObject("drawing").fields()
            assertEquals(
                "$example",
                BridgeMessage.PressedDrawing(
                    src = said.string("src")!!,
                    link = said.string("link")?.ifEmpty { null },
                    width = said.int("width"),
                    height = said.int("height")
                ),
                (parse(example) as BridgeMessage.Pressed).drawing
            )
        }
        assertTrue(
            "A press off a drawing is one, with no drawing",
            examples("pressed").any { it.isNull("drawing") && parse(it) == BridgeMessage.Pressed(null) }
        )
    }

    @Test
    fun everyOtherMessageIsIgnored() {
        val others = messages.keys().asSequence().toList().filter { it !in expected }
        // The ones this app is known to have no use for, so the test is seen to be testing.
        assertTrue(others.containsAll(listOf("unread", "painter", "window", "caption", "password", "notify")))
        for (type in others) {
            for (example in examples(type)) {
                assertNull("$type: $example", parse(example))
            }
        }
    }

    // -- What the app is ----------------------------------------------------------------------

    @Test
    fun theUserAgentSaysAndroidAndGooglePlay() {
        val agents = contract.getJSONArray("userAgents").objects().map { it.fields() }
        val ours = " " + Site.USER_AGENT_SUFFIX
        assertTrue(
            "No user agent in the contract is Android's selling on Google Play, ending \"$ours\"",
            agents.any { it["app"] == "android" && it["store"] == "google" && it.string("agent")!!.endsWith(ours) }
        )
        for (agent in agents.filter { it["app"] == null }) {
            assertFalse("${agent["agent"]} is no app's, and ends as ours", agent.string("agent")!!.endsWith(ours))
        }
        for (agent in agents.filter { it.string("agent")!!.endsWith(ours) }) {
            assertEquals(agent.string("agent"), "android", agent["app"])
            assertEquals(agent.string("agent"), "google", agent["store"])
        }
    }

    // -- What the app calls -------------------------------------------------------------------

    @Test
    fun theAppLeavesAPageTheWayEveryAppDoes() {
        assertEquals(contract.getJSONObject("scripts").getString("leaving"), PageScripts.LEAVING)
    }

    /** Every script the app evaluates in a page. */
    private val appScripts = listOf(
        PageScripts.LEAVING,
        PageScripts.pushToken("token"),
        PageScripts.signInAnswer(GoogleSignInMessages.token("a.b.c")),
        PageScripts.signInAnswer(GoogleSignInMessages.CANCELLED),
        PageScripts.signInAnswer(GoogleSignInMessages.FAILED),
        PageScripts.SIGN_IN_RESUME,
        PageScripts.SIGN_IN_UNOPENED,
        PageScripts.storePrices(mapOf("supporter_pack_2026" to "₩5,500")),
        PageScripts.storePurchased(listOf("token")),
        PageScripts.storeEnded(PlayBilling.Outcome.CANCELLED)
    )

    @Test
    fun everythingTheAppCallsIsOnThePage() {
        val members = contract.getJSONArray("members").let { a -> (0 until a.length()).map { a.getString(it) } }.toSet()
        // A member's parents are asked for before it, as `window.oeeeApp.signIn && ...`.
        val parents = members.flatMap { member ->
            val parts = member.split('.')
            (1 until parts.size).map { parts.take(it).joinToString(".") }
        }.toSet()
        val reference = Regex("""window\.oeeeApp\.([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)(\s*\()?""")
        for (script in appScripts) {
            val found = reference.findAll(script).toList()
            assertTrue("$script calls nothing on window.oeeeApp", found.any { it.groupValues[2].isNotEmpty() })
            for (match in found) {
                val path = match.groupValues[1]
                val called = match.groupValues[2].isNotEmpty()
                if (called) {
                    assertTrue("$script calls $path, which is not a member", path in members)
                } else {
                    assertTrue("$script asks for $path, which is not a member", path in members || path in parents)
                }
            }
        }
    }

    @Test
    fun aPushTokenIsHandedOverAsAStringWhateverItHolds() {
        val script = PageScripts.pushToken("a\"b\\c d")
        val argument = script.substringAfter("pushToken(").substringBeforeLast(");")
        assertEquals("a\"b\\c d", JSONTokener(argument).nextValue())
    }
}

/**
 * The object as Kotlin's own values, nested ones and all, with JSON null as null. The tests
 * are compiled against Android's org.json, which has no `toMap`, and run on Maven's.
 */
internal fun JSONObject.fields(): Map<String, Any?> =
    keys().asSequence().associateWith { value(get(it)) }

private fun value(json: Any?): Any? = when (json) {
    JSONObject.NULL -> null
    is JSONObject -> json.fields()
    is JSONArray -> (0 until json.length()).map { value(json.get(it)) }
    else -> json
}
