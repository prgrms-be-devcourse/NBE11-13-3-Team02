// 카테고리 API는 children이 중첩된 트리로 내려온다. 선택 목록(Select/사이드바)에 쓰려면
// 평평하게 펼쳐야 하위 카테고리도 빠짐없이 보인다 - depth만큼 들여쓰기 표시를 붙여 계층을 표현한다.
export function flattenCategories(nodes, depth = 0) {
  return (nodes ?? []).flatMap((node) => [
    { id: node.id, name: `${'— '.repeat(depth)}${node.name}` },
    ...flattenCategories(node.children, depth + 1),
  ])
}

// 중첩 트리에서 id로 노드를 찾는다 (하위 카테고리도 검색됨). id는 "" 등 falsy일 수 있어 먼저 걸러낸다.
export function findCategoryById(nodes, id) {
  if (!id) return null
  for (const node of nodes ?? []) {
    if (String(node.id) === String(id)) return node
    const found = findCategoryById(node.children, id)
    if (found) return found
  }
  return null
}
