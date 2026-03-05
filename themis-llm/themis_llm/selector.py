from collections import deque
from typing import Dict, List


class PublicInterfaceSelector:
    def rank(self, target_function: str, interfaces: List[str], adjacency: Dict[str, List[str]]) -> List[str]:
        scored = []
        for interface in interfaces:
            scored.append((self.hops(interface, target_function, adjacency), 0 if self.is_top_level(interface, adjacency) else 1, interface))
        scored.sort()
        return [item[2] for item in scored]

    def hops(self, start: str, end: str, adjacency: Dict[str, List[str]]) -> int:
        if start == end:
            return 0
        queue = deque([(start, 0)])
        seen = {start}
        while queue:
            node, dist = queue.popleft()
            for nxt in adjacency.get(node, []):
                if nxt in seen:
                    continue
                if nxt == end:
                    return dist + 1
                seen.add(nxt)
                queue.append((nxt, dist + 1))
        return 10 ** 9

    def is_top_level(self, method: str, adjacency: Dict[str, List[str]]) -> bool:
        if method.endswith("main") or method.endswith("main()"):
            return True
        for _, callees in adjacency.items():
            if method in callees:
                return False
        return True
