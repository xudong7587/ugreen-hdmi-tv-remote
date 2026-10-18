# v1 实验协议

TCP，UTF-8 JSON，一行一帧，含换行最多 4096 字节。所有 nonce/mac 使用小写十六进制。

1. 服务端发送 `{"protocol":1,"nonce":S}`，S 为 32 随机字节的 hex。
2. 客户端生成同长度 C，发送 `{"nonce":C,"proof":HMAC(token,"client|S|C")}`。
3. 服务端回复 `{"proof":HMAC(token,"server|S|C")}`，客户端必须验证。
4. K = hex 解码 HMAC(token,"session|S|C")。token 是归一化 32 字符配对码的 ASCII 字节，不做 hex 解码。
5. 服务端发送签名 seq=0、payload `{"status":"ready","protocol":1}` 或 busy；busy 后断开。
6. 客户端 seq 从 1 连续递增，逐帧等待相同 seq 的服务端 ACK。

签名帧：`{"seq":1,"payload":"标准Base64(JSON字节)","mac":"hex HMAC"}`。
MAC = HMAC(K, `方向|序号|payload字符串`)，客户端方向 c、服务端 s。验证原始 payload 字符串，不重新序列化。

请求：

```json
{"type":"key","key":"UP","action":"down"}
{"type":"key","key":"UP","action":"repeat"}
{"type":"key","key":"UP","action":"up"}
{"type":"ping"}
{"type":"release"}
```

key 支持 UP/DOWN/LEFT/RIGHT/OK/BACK/MENU，ACK 为 `{"status":"ok"}`。认证或格式错误直接断开，若拥有输入设备则释放。重复 down 不重复触发；未按下的 repeat/up 无效果。

此协议提供认证和完整性，不提供保密性，也不保证对已获配对码的客户端隔离。正式产品优先评估成熟 TLS 配对设计。
