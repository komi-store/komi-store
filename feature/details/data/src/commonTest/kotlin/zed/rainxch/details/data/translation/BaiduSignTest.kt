package zed.rainxch.details.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals

class BaiduSignTest {
    @Test
    fun matches_the_documented_baidu_signature() {
        assertEquals(
            "f89f9594663708c1605f3d736d01d2d4",
            baiduSign(
                appId = "2015063000000001",
                text = "apple",
                salt = "1435660288",
                appSecret = "12345678",
            ),
        )
    }
}
