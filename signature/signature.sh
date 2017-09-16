#!/bin/sh

# 转换平台签名命令
./keytool-importkeypair -k droisupershot.jks -p zhuoyisupershot -pk8 platform.pk8 -cert platform.x509.pem -alias droisupershot

# droisupershot.jks : 超级截屏签名文件
# zhuoyisupershot : 超级截屏签名文件密码
# platform.pk8、platform.x509.pem : 平台签名文件
# droisupershot : 超级截屏签名文件别名
