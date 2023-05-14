

# fofa攻击面梳理比较脚本
    比较fofa攻击面梳理的结果与 [chaos](https://chaos.projectdiscovery.io) 数据集的差异

    使用方法:
    安装 [babashka](https://github.com/babashka/babashka#installation)，如果要进行dns解析，
    还需要安装 [dnsx](https://github.com/projectdiscovery/dnsx#installation-instructions)

    *使用下载的原始域名进行比较* :
    ```shell
    # -f 指定fofa攻击面梳理下载的文件, (暂不支持ip-domain格式)
    ./diff.clj -p /tmp/paypal -f /tmp/paypal.csv  -o /tmp/diff.html
    ```

    *使用解析后的数据进行比较* :
    ``` shell
    # 先生成解析后的数据
    ./dns_resolve.clj

    # 生成比较文件, 使用-a参数 表示使用解析后的数据进行比较
    ./diff.clj -p /tmp/paypal -a -o /tmp/diff.html
    ```
