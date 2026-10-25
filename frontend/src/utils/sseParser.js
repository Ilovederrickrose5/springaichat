/**
 * 解析 SSE 缓冲区中的完整事件。
 *
 * Spring 会把含换行的文本编码成多行 `data:`，所以同一事件内的多行必须用 `\n`
 * 重新拼接，否则标题、列表等 Markdown 语法会丢失行语义。
 *
 * @param {string} buffer 累积的原始文本（可能包含不完整事件）
 * @returns {{chunks: string[], rest: string}} chunks 为已完整事件的数据内容，rest 为未结束的尾部
 */
export function parseSseEvents(buffer) {
  const parts = buffer.split(/\r?\n\r?\n/)
  const rest = parts.pop() ?? ''
  const chunks = []

  for (const event of parts) {
    const dataLines = event.split(/\r?\n/).filter(line => line.startsWith('data:'))
    if (!dataLines.length) continue
    chunks.push(dataLines.map(line => line.slice(5)).join('\n'))
  }

  return { chunks, rest }
}
