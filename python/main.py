import requests, ddddocr
from bs4 import BeautifulSoup as Psoup


"""
    >>> pip install requests
    >>> pip install bs4
    >>> pip install ddddocr
"""


USERNAME = ""  # 学号
PASSWORD = ""  # 门户密码
SUBMIT = False  # 是否提交，True 提交，False 只保存不提交


class User:
    __root = "http://jwxt.gdufe.edu.cn"
    __base = "/jsxsd"
    __captcha = "/verifycode.servlet"
    __login = "/xk/LoginToXkLdap"
    __list = "/xspj/xspj_find.do"
    __evaluate = "/xspj/xspj_save.do"
    
    def __init__(self, name, pwd):
        """登录"""
        print("本项目完全免费。我们的 Github 仓库是 https://github.com/Kiteio/easy-evaluate。如果对您有帮助，请花点时间为我们点亮 Star。")

        if name == "" or pwd == "":
            raise Exception("请参照文档填写您的账号信息。")

        with requests.Session() as session:
            self.__session = session
        
        # 获取 Cookie
        self.__session.get(self.__root + self.__base)

        for count in range(0, 5):
            # 获取验证码
            response = self.__session.get(self.__root + self.__base + self.__captcha)
            # 识别验证码
            ocr = ddddocr.DdddOcr(show_ad=False)
            text = ocr.classification(response.content)
            # 发送登录请求
            data = {
                "USERNAME": name,
                "PASSWORD": pwd,
                "RANDOMCODE": text
            }
            response = self.__session.post(self.__root + self.__base + self.__login, data=data)
            # 验证登录结果
            soup = Psoup(response.text, "html.parser")
            if soup.find("title").text == "学生个人中心":
                return
        raise Exception("超出最大重试次数，登录失败，请检查信息后重试。")
    
    def evaluate_list(self):
        """获取评教列表"""
        params = {
            "Ves632DSdyV": "NEW_XSD_JXPJ"
        }
        response = self.__session.get(self.__root + self.__base + self.__list, params=params)
        soup = Psoup(response.text, "html.parser")
        
        table = soup.find("table", class_="Nsb_r_list Nsb_table")
        rows = table.find_all("tr")
        
        if len(rows) < 2:
            raise Exception("当前不在评教时间")
        else:
            items = rows[1].find_all("td")[6].find_all("a")
            result = []
            
            for item in items:
                sort = item.text

                url = self.__root + item["href"]
                res = self.__session.get(url)
    
                soup = Psoup(res.text, "html.parser")
                info_list = soup.find("table", id="dataList").find_all("tr")
    
                for index in range(1, len(info_list)):
                    info = info_list[index].find_all("td")

                    # 过滤已评
                    if info[7].a.text == "查看":
                        print(f"课程 [{info[2].text}] 已评价")
                        continue

                    try:
                        result.append({
                            "id": info[1].text,
                            "name": info[2].text,
                            "teacher": info[3].text,
                            "sort": sort,
                            "url": self.__root + info[7].a["onclick"][7:-12]
                        })
                    except:
                        print(f"课程 [{info[2].text}] 异常，已跳过")
            
            return result

    def evaluate(self, result, submit):
        """评价"""
        for item in result:
            # 进入课程进行评教
            res = self.__session.get(item["url"])
            # 构建表单
            form = []
            soup = Psoup(res.text, "html.parser")
            table = soup.find("form", id="Form1")
            children = table.find_all("input")
            
            b = False
            for i in range(len(children) - 3):
                if i < 10:
                    if i == 0:
                        form.append(("issubmit", "1" if submit else "0"))
                        continue
                    form.append((children[i]["name"], children[i]["value"]))
                else:
                    sub = i - 10
    
                    mod = sub % 11
                    if mod == 0:
                        b = (sub + 1) % 2 == 0
    
                    if (b and sub % 2 == 0) or (not b and sub % 2 != 0):
                        continue
                    
                    if mod == 2:
                        form.append((children[i - 1]["name"], children[i - 1]["value"]))
    
                    form.append((children[i]["name"], children[i]["value"]))
    
            self.__session.post(self.__root + self.__base + self.__evaluate, data=form)
            print(f"[{'提交' if submit else '已保存 | 未提交'}] {item['id']} {item['name']} {item['teacher']} {item['sort']}")


if __name__ == "__main__":
    user = User(USERNAME, PASSWORD)
    evaluate_list = user.evaluate_list()
    user.evaluate(evaluate_list, SUBMIT)
