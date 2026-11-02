package me.rerere.momoly.data.character

/** 用户在写入任何资源前取消导入，和格式错误分开处理。 */
class CharacterImportCancelledException : Exception("Character import cancelled")
