

# fofa攻击面梳理比较脚本

比较fofa攻击面梳理的结果与 [chaos](https://chaos.projectdiscovery.io) 数据集的差异

## 使用方法

### 安装依赖

安装 [babashka](https://github.com/babashka/babashka#installation),用于脚本执行

安装 [dnsx](https://github.com/projectdiscovery/dnsx#installation-instructions), 用于dns解析

安装 [httpx](https://github.com/projectdiscovery/httpx#installation-instructions), 用于http请求


# 比较方案
  统一格式化为chaos db的格式，针对一个目标使用单独的目录存放，每个根域名一个文件，保存子域名列表。

  比较的维度:
1. **数据覆盖性**：这涉及到工具所能搜集的资产的全面性。你可以给每个工具提供相同的目标（如一组相同的域名），然后比较每个工具搜集到的子域名、IP地址、证书信息等的数量。

2. **数据质量**：这涉及到工具搜集的资产数据的准确性和可用性。你可以对每个工具搜集的数据进行检查，看看有多少数据是准确的（如能正确解析的域名），有多少数据是可用的（如能访问的web服务）。

3. **数据深度**：这涉及到工具能够搜集到的资产信息的深度。你可以对每个工具搜集的数据进行深度分析，看看它们能提供多少额外的信息，如ASN信息、证书链信息等。

4. **性能**：这涉及到工具在搜集和处理数据时的效率。你可以比较每个工具处理相同数量的资产数据所需要的时间。

5. **易用性**：这涉及到工具的使用难度。你可以对每个工具的安装、配置和使用过程进行评估，看看它们的使用难度如何。


比较域名数量的方法:

   先比较原始的列表，

   然后通过dnsx比较通过域名解析后有效的列表，

   再通过httpx比较http可以访问的列表。

# 比较步骤

1.  准备数据集

从[chaos](https://chaos.projectdiscovery.io/) 下载对应的数据集

使用[fofa](https://fofa.info/extensions/assets) 的攻击面梳理添加线索，下载结果csv


2. 格式化数据

转换fofa下载的数据为chaos db的形式:

```shell
# -f 输入文件，必须有domain列  -o 输出的文件夹
./fofa_format.clj -o ./paypal/fofa -f ./paypal/fofa/all.csv

# 如果是ip domain格式的数据，使用 -t 参数:
./fofa_format.clj -f paypal/fofa/ip_subdomain.csv -t domain -o ./paypal/fofa/
```


3. 使用原始数据进行比较:
```shell
# -d1 指定第一个比较源， -d2 指定第二个比较源
./diff.clj -d1 ./paypal/fofa -d2 ./paypal/chaos -o ./diff.html
```


4. 使用dns解析后的数据进行比较 :

对数据进行dns解析:
```shell
./dns_resolve.clj -p paypal/fofa -o paypal/fofa-dns
./dns_resolve.clj -p ./paypal/chaos -o paypal/chaos-dns
```

进行diff
``` shell
./diff.clj -d1 ./paypal/fofa-dns -d2 ./paypal/chaos-dns -o dns-diff.html
```


5. 使用http probe后的数据进行比较 :

检测数据http访问:
```shell
# 可以使用-k 3保留内容相似的前3个域名，默认为5
./http_check.clj -p paypal/fofa -o ./paypal/fofa-http
./http_check.clj -p paypal/chaos -o ./paypal/chaos-http
```

执行diff:
``` shell
./diff.clj -d1 ./paypal/fofa-http -d2 ./paypal/chaos-http  -o http-diff.html
```

