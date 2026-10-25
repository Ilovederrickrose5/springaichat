import test from 'node:test'
import assert from 'node:assert/strict'

import { parseSseEvents } from './sseParser.js'

// Spring MVC 的 SseEventBuilderImpl.data() 写入 "data:"（冒号后无空格），
// 并把内容中的 \n 替换为 "\ndata:"，因此多行内容会变成连续的 data: 行。
test('多行 data: 用 \\n 重拼，保留 Markdown 换行', () => {
  const { chunks, rest } = parseSseEvents('data:## 标题\ndata:\ndata:- a\ndata:- b\n\n')

  assert.deepEqual(chunks, ['## 标题\n\n- a\n- b'])
  assert.equal(rest, '')
})

test('空事件与注释行不产生 chunk', () => {
  const { chunks, rest } = parseSseEvents(': ping\n\n\n\n')

  assert.deepEqual(chunks, [])
  assert.equal(rest, '')
})

test('跨 chunk 边界：未结束事件留在 rest，补齐后不丢内容', () => {
  const first = parseSseEvents('data:第一段')
  assert.deepEqual(first.chunks, [])
  assert.equal(first.rest, 'data:第一段')

  const second = parseSseEvents(first.rest + '\ndata:第二段\n\n')
  assert.deepEqual(second.chunks, ['第一段\n第二段'])
  assert.equal(second.rest, '')
})

test('{"error":...} 事件原样返回，兼容旧错误分支', () => {
  const payload = '{"error":"用户未登录，请先登录"}'
  const { chunks } = parseSseEvents(`data:${payload}\n\n`)

  assert.deepEqual(chunks, [payload])
  assert.ok(chunks[0].startsWith('{"error":'))
})

test('一次缓冲区包含多个事件', () => {
  const { chunks, rest } = parseSseEvents('data:a\n\ndata:b\n\n')

  assert.deepEqual(chunks, ['a', 'b'])
  assert.equal(rest, '')
})
