package ink.awning.easy.evaluate

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.sourceforge.tess4j.Tesseract
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO


const val USERNAME = "" // 学号
const val PASSWORD = "" // 门户密码
const val SUBMIT = false // 是否提交，true 提交，false 只保存不提交

private val client = HttpClient(CIO)


suspend fun main() {
    User.login(USERNAME, PASSWORD).apply {
        evaluate(evaluateList(), SUBMIT)
    }
}


class User(private val cookie: String?) {
    /**
     * 获取评教列表
     * @return [List]<[Item]>
     */
    suspend fun evaluateList(): List<Item> {
        val response = client.get(ROOT + BASE + LIST) {
            parameter("Ves632DSdyV", "NEW_XSD_JXPJ")
            header(HttpHeaders.Cookie, cookie)
        }

        val document = Ksoup.parse(response.bodyAsText())
        val table = document.getElementsByClass("Nsb_r_list Nsb_table").first()!!
        val rows = table.getElementsByTag("tr")

        if (rows.size == 2) {
            val items = rows[1].getElementsByTag("td")[6].getElementsByTag("a")

            val result = arrayListOf<Item>()
            for(item in items) {
                val sort = item.text()
                val url = ROOT + item.attr("href")

                val infoList = client.get(url) {
                    header(HttpHeaders.Cookie, cookie)
                }.bodyAsText().run { Ksoup.parse(this@run) }
                    .getElementById("dataList")!!
                    .getElementsByTag("tr")

                for (j in 1..<infoList.size) {
                    val info = infoList[j].getElementsByTag("td")
                    val href = info[7].getElementsByTag("a").first()!!.attr("onclick")

                    // 过滤已评价
                    if (href.isBlank()) {
                        println("课程 [${info[2].text()}] 已评价")
                        continue
                    }

                    result.add(
                        Item(
                            info[1].text(),
                            info[2].text(),
                            info[3].text(),
                            sort,
                            ROOT + href.substring(7, href.length - 12)
                        )
                    )
                }
            }
            return result
        }else error("当前不在评教时间")
    }


    /**
     * 评价列表
     * @param items
     * @param submit
     */
    suspend fun evaluate(items: List<Item>, submit: Boolean) {
        for (item in items) {
            val response = client.get(item.url) {
                header(HttpHeaders.Cookie, cookie)
            }
            val table = Ksoup.parse(response.bodyAsText()).getElementById("Form1")!!
            val children = table.children()

            val parametersBuilder = ParametersBuilder()
            for (index in 0..<children.size - 2) {
                val key = children[index].attr("name")
                val value = if (key == "issubmit") (if (submit) "1" else "0") else
                    children[index].attr("value")
                parametersBuilder.append(key, value)
            }

            val rows = table.getElementById("table1")!!
                .getElementsByTag("tr")

            for (index in 1..<rows.size) {
                val info = rows[index].children()
                if (info.size == 2) {
                    val first = info[0].child(0)
                    parametersBuilder.append(
                        first.attr("name"),
                        first.attr("value")
                    )

                    val options = info[1].children()
                    for (optionIndex in 1..<options.size step 2) {
                        if (optionIndex == 1) {
                            parametersBuilder.append(
                                options.first()!!.attr("name"),
                                options.first()!!.attr("value")
                            )
                        }
                        parametersBuilder.append(
                            options[optionIndex].attr("name"),
                            options[optionIndex].attr("value")
                        )
                    }
                }
            }

            client.submitForm(
                ROOT + BASE + EVALUATE,
                parametersBuilder.build()
            ) {
                header(HttpHeaders.Cookie, cookie)
            }.also {
                println(it.bodyAsText())
            }
            println("[${if( submit) "提交" else "已保存 | 未提交"}] ${item.id} ${item.name} ${item.teacher} ${item.sort}")
        }
    }


    companion object {
        private const val ROOT: String = "http://jwxt.gdufe.edu.cn"
        private const val BASE: String = "/jsxsd"
        private const val CAPTCHA: String = "/verifycode.servlet"
        private const val LOGIN: String = "/xk/LoginToXkLdap"
        private const val LIST: String = "/xspj/xspj_find.do"
        private const val EVALUATE: String = "/xspj/xspj_save.do"


        suspend fun login(name: String, pwd: String): User {
            println("本项目完全免费。我们的 Github 仓库是 https://github.com/Kiteio/easy-evaluate。如果对您有帮助，请花点时间为我们点亮 Star。")

            if (name.isBlank() || pwd.isBlank()) {
                error("请参照文档填写您的账号信息。")
            }

            for (i in 0..<10) {
                // 获取 Cookie
                val cookie = client.get(ROOT + BASE).headers[HttpHeaders.SetCookie]

                // 获取验证码
                val text = client.get(ROOT + BASE + CAPTCHA) {
                    header(HttpHeaders.Cookie, cookie)
                }.readBytes().readText()

                // 发送登录请求
                val response = client.submitForm(
                    ROOT + BASE + LOGIN,
                    parameters {
                        set("USERNAME", name)
                        set("PASSWORD", pwd)
                        set("RANDOMCODE", text)
                    }
                ) {
                    header(HttpHeaders.Cookie, cookie)
                }

                if (response.bodyAsText().isEmpty()) return User(cookie)
            }

            error("超出最大重试次数，登录失败，请检查信息后重试。")
        }


        /**
         * OCR
         * @receiver [ByteArray]
         * @return [String]
         */
        private fun ByteArray.readText() = Tesseract().run {
            setDatapath(getResourcePath("tessdata/"))
            setLanguage("eng")
            val bufferedImage = ImageIO.read(ByteArrayInputStream(this@readText)).run {
                getSubimage(3, 3, width - 17, height - 7)
            }.apply {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        setRGB(x, y, getRGB(x, y).binary())
                    }
                }
            }
            doOCR(bufferedImage).filter { it.isLetterOrDigit() }
        }


        /**
         * 二值化
         * @receiver [Int]
         * @param threshold
         * @return [Int]
         */
        private fun Int.binary(threshold: Int = 113): Int {
            val red = this shr 16 and 0xff
            val green = this shr 8 and 0xff
            val blue = this and 0xff
            var gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
            gray = if (gray > threshold) 255 else 0
            return (gray shl 16) + (gray shl 8) + gray
        }


        /**
         * 获取资源目录下的路径
         * @param path
         * @return [String]
         */
        private fun getResourcePath(path: String) = (User::class.java.getResource("")!!.path.replace(
            "build/classes/kotlin/main/ink/awning/easy/evaluate",
            "src/main/resources"
        ) + path).substring(1)
    }
}


/**
 * 评教列表项
 * @property id 课程编号
 * @property name 课程名
 * @property teacher 教师
 * @property sort 课程分类
 * @property url 评教地址
 * @constructor
 */
data class Item(val id: String, val name: String, val teacher: String, val sort: String, val url: String)