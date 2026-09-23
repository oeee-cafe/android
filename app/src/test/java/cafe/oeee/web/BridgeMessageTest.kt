package cafe.oeee.web

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The messages as app_bridge.jinja (oeee-cafe/web) writes them. What matters most is what
 * the app does with a message it does not expect: nothing, rather than something wrong.
 */
class BridgeMessageTest {
    @Test
    fun page() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"page","path":"/draw","signedIn":true,"presence":"drawing",""" +
                """"community":"오이카페","group":null,"painting":true}"""
        )
        assertEquals(
            BridgeMessage.Page(
                signedIn = true,
                painting = true
            ),
            message
        )
    }

    @Test
    fun aPageWithoutTheToolbarCannotSayWhoIsSignedIn() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"page","path":"/","signedIn":null,"presence":null,"painting":false}"""
        ) as BridgeMessage.Page
        assertNull(message.signedIn)
    }

    @Test
    fun unread() {
        assertEquals(BridgeMessage.Unread(12), BridgeMessage.parse("""{"v":1,"type":"unread","count":12}"""))
        assertEquals(BridgeMessage.Unread(0), BridgeMessage.parse("""{"v":1,"type":"unread","count":-3}"""))
    }

    @Test
    fun theme() {
        assertEquals(
            BridgeMessage.Theme(ground = Color(23, 23, 43), grid = Color(34, 34, 63)),
            BridgeMessage.parse("""{"v":1,"type":"theme","choice":"dark","ground":"#17172b","grid":"#22223f"}""")
        )
        // A colour this app cannot read is none, and the app's own is drawn.
        assertEquals(
            BridgeMessage.Theme(ground = null, grid = null),
            BridgeMessage.parse("""{"v":1,"type":"theme","choice":"dark","ground":"red","grid":null}""")
        )
    }

    @Test
    fun themeGroundAndGrid() {
        // The design system's tokens read back as they were written, which is hex.
        val message = BridgeMessage.parse(
            """{"v":1,"type":"theme","choice":"light","ground":"#ccccff","grid":" #bbf "}"""
        ) as BridgeMessage.Theme
        assertEquals(Color(204, 204, 255), message.ground)
        assertEquals(Color(187, 187, 255), message.grid)
        // A page without the design system's stylesheet has neither, and a build of the site
        // from before them says nothing at all.
        val none = BridgeMessage.parse(
            """{"v":1,"type":"theme","choice":"dark","ground":null,"grid":""}"""
        ) as BridgeMessage.Theme
        assertNull(none.ground)
        assertNull(none.grid)
        val older = BridgeMessage.parse("""{"v":1,"type":"theme","choice":"dark","dark":true}""") as BridgeMessage.Theme
        assertNull(older.ground)
    }

    @Test
    fun words() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"words","leaveTitle":"이 페이지를 떠날까요?","leaveBody":"저장하지 않은 내용은 사라집니다.",""" +
                """"leave":"떠나기","stay":"머무르기","ok":"확인","cancel":"취소","saveImage":"이미지 저장",""" +
                """"copyImage":"이미지 복사","share":"공유…","copyLink":"링크 복사","savedImage":"사진에 저장했습니다",""" +
                """"savedFile":"다운로드에 저장했습니다","saveFailed":"저장하지 못했습니다",""" +
                """"steamSignInFailed":"Steam으로 로그인하지 못했습니다."}"""
        )
        assertEquals(
            BridgeMessage.Words(
                leaveTitle = "이 페이지를 떠날까요?",
                leaveBody = "저장하지 않은 내용은 사라집니다.",
                leave = "떠나기",
                stay = "머무르기",
                ok = "확인",
                cancel = "취소",
                saveImage = "이미지 저장",
                copyImage = "이미지 복사",
                share = "공유…",
                copyLink = "링크 복사",
                savedImage = "사진에 저장했습니다",
                savedFile = "다운로드에 저장했습니다",
                saveFailed = "저장하지 못했습니다"
            ),
            message
        )
    }

    @Test
    fun wordsThePageLeftOutAreTheAppsOwn() {
        val message = BridgeMessage.parse("""{"v":1,"type":"words","leave":"Leave","stay":"","ok":null}""") as BridgeMessage.Words
        assertEquals("Leave", message.leave)
        assertNull(message.stay)
        assertNull(message.ok)
        assertNull(message.saveImage)
    }

    @Test
    fun signIn() {
        assertEquals(
            BridgeMessage.SignIn(provider = "google", nonce = "n0nc3"),
            BridgeMessage.parse("""{"v":1,"type":"signIn","provider":"google","nonce":"n0nc3"}""")
        )
        // Nothing to sign in for: no nonce, or no provider to ask.
        assertNull(BridgeMessage.parse("""{"v":1,"type":"signIn","provider":"google"}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"signIn","provider":"google","nonce":""}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"signIn","provider":"google","nonce":42}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"signIn","nonce":"n0nc3"}"""))
    }

    @Test
    fun browse() {
        assertEquals(
            BridgeMessage.Browse("https://oeee.cafe/auth/handoff/abc"),
            BridgeMessage.parse("""{"v":1,"type":"browse","url":"https://oeee.cafe/auth/handoff/abc"}""")
        )
        assertNull(BridgeMessage.parse("""{"v":1,"type":"browse","url":""}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"browse"}"""))
    }

    @Test
    fun share() {
        assertEquals(
            BridgeMessage.Share(title = "A drawing", text = "Look\nhttps://oeee.cafe/@reader/9c881320"),
            BridgeMessage.parse(
                """{"v":1,"type":"share","title":"A drawing","text":"Look\nhttps://oeee.cafe/@reader/9c881320"}"""
            )
        )
        assertEquals(
            BridgeMessage.Share(title = "", text = "https://oeee.cafe/"),
            BridgeMessage.parse("""{"v":1,"type":"share","text":"https://oeee.cafe/"}""")
        )
        // Nothing to share.
        assertNull(BridgeMessage.parse("""{"v":1,"type":"share","title":"A drawing","text":""}"""))
    }

    @Test
    fun download() {
        assertEquals(
            BridgeMessage.Download(name = "drawing.png", data = "data:image/png;base64,iVBORw0KGgo="),
            BridgeMessage.parse(
                """{"v":1,"type":"download","name":"drawing.png","data":"data:image/png;base64,iVBORw0KGgo="}"""
            )
        )
        // A file with no name still has its data URL to say what it is.
        assertEquals(
            BridgeMessage.Download(name = "", data = "data:text/plain,hi"),
            BridgeMessage.parse("""{"v":1,"type":"download","data":"data:text/plain,hi"}""")
        )
        assertNull(BridgeMessage.parse("""{"v":1,"type":"download","name":"drawing.png"}"""))
        // As app_polyfills.jinja sends it: the file's own type as `mime`, which the app does
        // not need, because the data URL says the same thing.
        assertEquals(
            BridgeMessage.Download(name = "drawing.png", data = "data:image/png;base64,iVBORw0KGgo="),
            BridgeMessage.parse(
                """{"name":"drawing.png","mime":"image/png","data":"data:image/png;base64,iVBORw0KGgo=","v":1,"type":"download"}"""
            )
        )
    }

    @Test
    fun haptic() {
        assertEquals(BridgeMessage.Haptic("success"), BridgeMessage.parse("""{"v":1,"type":"haptic","name":"success"}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"haptic"}"""))
    }

    @Test
    fun pressed() {
        assertEquals(
            BridgeMessage.Pressed(
                BridgeMessage.PressedDrawing(
                    src = "https://r2.oeee.cafe/image/a.png",
                    link = "https://oeee.cafe/@reader/9c881320",
                    width = 300,
                    height = 300
                )
            ),
            BridgeMessage.parse(
                """{"v":1,"type":"pressed","drawing":{"src":"https://r2.oeee.cafe/image/a.png",""" +
                    """"link":"https://oeee.cafe/@reader/9c881320","width":300,"height":300}}"""
            )
        )
    }

    @Test
    fun aDrawingOnItsOwnPostPageHasNoLink() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"pressed","drawing":{"src":"https://r2.oeee.cafe/a.png","link":"","width":1,"height":2}}"""
        ) as BridgeMessage.Pressed
        assertNull(message.drawing?.link)
    }

    @Test
    fun aPressOffADrawingOrOnOneTheAppCannotFetchIsNone() {
        assertEquals(BridgeMessage.Pressed(null), BridgeMessage.parse("""{"v":1,"type":"pressed","drawing":null}"""))
        assertEquals(
            BridgeMessage.Pressed(null),
            BridgeMessage.parse("""{"v":1,"type":"pressed","drawing":{"src":"blob:https://oeee.cafe/1","width":1,"height":1}}""")
        )
    }

    @Test
    fun painter() {
        assertEquals(BridgeMessage.Painter("ready"), BridgeMessage.parse("""{"v":1,"type":"painter","state":"ready"}"""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        assertEquals(
            BridgeMessage.Unread(1),
            BridgeMessage.parse("""{"v":1,"type":"unread","count":1,"invitations":1,"extra":{"a":[1,2]}}""")
        )
    }

    @Test
    fun whatTheAppDoesNotUnderstandIsNothing() {
        assertNull(BridgeMessage.parse(null))
        assertNull(BridgeMessage.parse(""))
        assertNull(BridgeMessage.parse("light"))
        assertNull(BridgeMessage.parse("{not json"))
        assertNull(BridgeMessage.parse("[1,2]"))
        assertNull(BridgeMessage.parse("\"page\""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"someday"}"""))
        assertNull(BridgeMessage.parse("""{"v":1}"""))
        // A later version of the contract may mean something else by the same names.
        assertNull(BridgeMessage.parse("""{"v":2,"type":"unread","count":1}"""))
        assertNull(BridgeMessage.parse("""{"type":"unread","count":1}"""))
    }
}
