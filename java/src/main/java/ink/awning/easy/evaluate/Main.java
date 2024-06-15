package ink.awning.easy.evaluate;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Main {
    static String USERNAME = ""; // 学号
    static String PASSWORD = ""; // 门户密码
    static boolean SUBMIT = false; // 是否提交，true 提交，false 只保存不提交


    public static void main(String[] args) throws Exception {
        User user = new User(USERNAME, PASSWORD);
        user.evaluate(user.evaluateList(), SUBMIT);
    }
}


class User {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new MyCookieJar())
            .build();

    private static final String root = "http://jwxt.gdufe.edu.cn";
    private static final String base = "/jsxsd";
    private static final String captcha = "/verifycode.servlet";
    private static final String login = "/xk/LoginToXkLdap";
    private static final String list = "/xspj/xspj_find.do";
    private static final String evaluate = "/xspj/xspj_save.do";


    User(@NotNull String name, @NotNull String pwd) throws Exception {
        println("本项目完全免费。我们的 Github 仓库是 https://github.com/Kiteio/easy-evaluate。如果对您有帮助，请花点时间为我们点亮 Star。");

        if (name.isBlank() || pwd.isBlank()) {
            println("请参照文档填写您的账号信息。");
            return;
        }

        // 获取 Cookie
        Request request = new Request.Builder()
                .url(root + base)
                .get()
                .build();
        client.newCall(request).execute();

        for (int count = 0; count < 10; count ++) {
            // 获取验证码
            request = new Request.Builder()
                    .url(root + base + captcha)
                    .get()
                    .build();
            Response response = client.newCall(request).execute();
            ResponseBody body = response.body();

            if (body != null) {
                // 发送登录请求
                FormBody formBody = new FormBody.Builder()
                        .add("USERNAME", name)
                        .add("PASSWORD", pwd)
                        .add("RANDOMCODE", readText(body.bytes()))
                        .build();
                request = new Request.Builder()
                        .url(root + base + login)
                        .post(formBody)
                        .build();
                body = client.newCall(request).execute().body();

                // 验证登录结果
                if (body != null) {
                    Document document = Jsoup.parse(body.string());

                    if (document.title().equals("学生个人中心")) return;
                }
            } else throw new Exception("未知错误: body 为 null");
        }
        throw new Exception("超出最大重试次数，登录失败，请检查信息后重试。");
    }


    /**
     * 获取评教列表
     *
     * @return 评教列表
     * @throws Exception
     */
    List<Item> evaluateList() throws Exception {
        ArrayList<Item> result = new ArrayList<>();

        Request request = new Request.Builder()
                .url(root + base + list + "?Ves632DSdyV=NEW_XSD_JXPJ")
                .get()
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();

        if (body != null) {
            Document document = Jsoup.parse(body.string());

            Element table = document.getElementsByClass("Nsb_r_list Nsb_table").getFirst();
            Elements rows = table.getElementsByTag("tr");

            if (rows.size() == 2) {
                Elements items = rows.get(1).getElementsByTag("td").get(6).getElementsByTag("a");

                for (Element item : items) {
                    String sort = item.text();
                    String url = root + item.attr("href");

                    request = new Request.Builder()
                            .url(url)
                            .get()
                            .build();

                    response = client.newCall(request).execute();
                    body = response.body();
                    if (body != null) {
                        Elements infoList = Jsoup.parse(body.string())
                                .getElementById("dataList")
                                .getElementsByTag("tr");

                        for (int j = 1; j < infoList.size(); j++) {
                            Elements info = infoList.get(j).getElementsByTag("td");
                            String href = info.get(7).getElementsByTag("a").getFirst().attr("onclick");

                            // filter finished
                            if (href.isBlank()) {
                                println("课程 [" + info.get(2).text() + "] 已评价");
                                continue;
                            }

                            try {
                                result.add(new Item(
                                        info.get(1).text(),
                                        info.get(2).text(),
                                        info.get(3).text(),
                                        sort,
                                        root + href.substring(7, href.length() - 12)
                                ));
                            } catch (Exception e) {
                                println("课程" + info.get(2).text() + "已跳过");
                            }
                        }
                    }
                }
            } else throw new Exception("当前不在评教时间");
        } else throw new Exception("未知错误");

        return result;
    }


    /**
     * 评价列表
     *
     * @param items  列表
     * @param submit 是否提交
     * @throws Exception
     */
    void evaluate(List<Item> items, boolean submit) throws Exception {
        for (Item item : items) {
            Request request = new Request.Builder()
                    .url(item.url)
                    .get()
                    .build();
            Response response = client.newCall(request).execute();
            ResponseBody body = response.body();

            if (body != null) {
                Element table = Jsoup.parse(body.string()).getElementById("Form1");

                FormBody.Builder formBodyBuilder = new FormBody.Builder();

                if (table != null) {
                    Elements children = table.children();
                    for (int index = 0; index < children.size() - 2; index++) {
                        String key = children.get(index).attr("name");
                        String value = key.equals("issubmit") ? (submit ? "1" : "0") : children.get(index).attr("value");
                        formBodyBuilder.add(key, value);
                    }

                    Elements rows = table.getElementById("table1").getElementsByTag("tr");

                    for (int index = 1; index < rows.size(); index++) {
                        Elements itemInfo = rows.get(index).children();

                        if (itemInfo.size() == 2) {
                            Element first = itemInfo.get(0).child(0);
                            formBodyBuilder.add(first.attr("name"), first.attr("value"));

                            Elements ops = itemInfo.get(1).children();

                            for (int opIndex = 1; opIndex < ops.size(); opIndex += 2) {
                                if (opIndex == 1) {
                                    formBodyBuilder.add(ops.getFirst().attr("name"), ops.getFirst().attr("value"));
                                }

                                formBodyBuilder.add(ops.get(opIndex).attr("name"), ops.get(opIndex).attr("value"));
                            }
                        }
                    }

                    request = new Request.Builder()
                            .url(root + base + evaluate)
                            .post(formBodyBuilder.build())
                            .build();

                    client.newCall(request).execute();
                    println("[" + (submit ? "提交" : "已保存 | 未提交") + "]" + " " + item.id + " " + item.name + " " + item.teacher + " " + item.sort);
                } else throw new Exception("未知错误: table 为 null");
            } else throw new Exception("未知错误: body 为 null");
        }
    }


    /**
     * 打印
     *
     * @param message
     */
    private static void println(@Nullable Object message) {
        System.out.println(message);
    }


    /**
     * OCR
     * @param bytes
     * @return
     * @throws IOException
     * @throws TesseractException
     */
    private static String readText(byte[] bytes) throws IOException, TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(getResourcePath("tessdata/"));
        tesseract.setLanguage("eng");
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));

        // 去除边框
        bufferedImage = bufferedImage.getSubimage(
                3, 3, bufferedImage.getWidth() - 17, bufferedImage.getHeight() - 7
        );

        // 二值化
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                int rgb = bufferedImage.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                double grayDouble = 0.299 * red + 0.587 * green + 0.114 * blue;
                int gray = (int) grayDouble;
                bufferedImage.setRGB(x, y, (gray << 16) + (gray << 8) + gray);
            }
        }

        return tesseract.doOCR(bufferedImage).replaceAll("[^a-zA-Z0-9]", "");
    }


    /**
     * 获取资源目录下的路径
     * @param path
     * @return
     */
    private static String getResourcePath(String path) {
        return (User.class.getResource("").getPath()
                .replace(
                        "target/classes/ink/awning/easy/evaluate",
                        "src/main/resources"
                ) + path).substring(1);
    }


    /**
     * CookieJar，用于缓存 Cookie
     */
    private static class MyCookieJar implements CookieJar {
        private final ArrayList<Cookie> cookies = new ArrayList<>();

        @NotNull
        @Override
        public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
            return cookies;
        }

        @Override
        public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {
            cookies.addAll(list);
        }
    }
}


/**
 * 评教列表项
 */
class Item {
    String id; // 课程编号
    String name; // 课程名
    String teacher; // 教师
    String sort; // 课程分类
    String url; // 评教地址


    public Item(String id, String name, String teacher, String sort, String url) {
        this.id = id;
        this.name = name;
        this.teacher = teacher;
        this.sort = sort;
        this.url = url;
    }
}