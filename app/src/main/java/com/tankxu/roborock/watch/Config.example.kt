package com.tankxu.roborock.watch

/**
 * 复制本文件为 Config.kt 并填入你自己的值。Config.kt 已被 .gitignore 排除, 不会上传。
 * (App 内点顶部状态条也能改, 存 SharedPreferences; 这里只是首次运行的默认值)
 */
object Config {
    const val VAC_IP = "192.168.1.100"                       // 扫地机起始IP(连不上会自动扫网段按DID找)
    const val VAC_TOKEN = "你的扫地机token32位hex"            // 获取方法见 README
    const val VAC_DID = 0L                                   // 扫地机设备ID(十进制)
}
