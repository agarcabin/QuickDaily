# 📈 每日复盘

1.  
2.  
3.  

# ⏱️ 最近修改
```base
formulas:
  未命名: file.mtime.relative()
properties:
  file.name:
    displayName: 页面名称
  formula.未命名:
    displayName: 最后一次编辑
views:
  - type: table
    name: 表格
    filters:
      and:
        - '!file.inFolder("/日记/")'
    order:
      - file.name
      - formula.未命名
    sort:
      - property: formula.未命名
        direction: DESC
    limit: 5

```
