import { useState } from 'react'
import Box from '@mui/material/Box'
import Popover from '@mui/material/Popover'
import Typography from '@mui/material/Typography'
import Collapse from '@mui/material/Collapse'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { findCategoryById } from '../utils/categoryTree'

// 카테고리 이름 하나를 눌렀을 때: 그 카테고리로 바로 선택됨 + 하위 카테고리가 있으면 펼쳐져서
// 더 구체적인 하위 카테고리도 이어서 고를 수 있다 (선택과 펼침을 한 번의 클릭으로).
function CategoryOption({ node, depth, value, expandedIds, onToggle, onSelect }) {
  const hasChildren = (node.children ?? []).length > 0
  const isExpanded = expandedIds.has(node.id)
  const isSelected = String(node.id) === value

  return (
    <Box>
      <Box
        onClick={() => onSelect(node)}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          pl: 1 + depth * 2,
          pr: 1.5,
          py: 0.9,
          cursor: 'pointer',
          borderRadius: 1,
          bgcolor: isSelected ? 'primary.light' : 'transparent',
          '&:hover': { bgcolor: isSelected ? 'primary.light' : 'action.hover' },
        }}
      >
        {hasChildren ? (
          <ChevronRightIcon
            fontSize="small"
            onClick={(e) => {
              e.stopPropagation()
              onToggle(node.id)
            }}
            sx={{
              color: 'text.secondary',
              transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
              transition: 'transform 0.15s',
            }}
          />
        ) : (
          <Box sx={{ width: 20, flexShrink: 0 }} />
        )}
        <Typography
          variant="body2"
          fontWeight={isSelected ? 700 : 400}
          color={isSelected ? 'primary.main' : 'text.primary'}
        >
          {node.name}
        </Typography>
      </Box>
      {hasChildren && (
        <Collapse in={isExpanded}>
          {node.children.map((child) => (
            <CategoryOption
              key={child.id}
              node={child}
              depth={depth + 1}
              value={value}
              expandedIds={expandedIds}
              onToggle={onToggle}
              onSelect={onSelect}
            />
          ))}
        </Collapse>
      )}
    </Box>
  )
}

// 카테고리 트리를 펼쳐가며 고를 수 있는 선택기. 기존 <Select>는 하위 카테고리를 "— 이름"처럼
// 밋밋하게 한 줄로 늘어놓기만 해서 뭐가 상위/하위인지 구분하기 불편했다 - 대신 눌러서 펼치는 트리로 고른다.
export default function CategoryTreeSelect({
  categories,
  value,
  onChange,
  label = '카테고리',
  size = 'small',
  fullWidth = false,
}) {
  const [anchorEl, setAnchorEl] = useState(null)
  const [expandedIds, setExpandedIds] = useState(new Set())
  const open = Boolean(anchorEl)

  const selected = findCategoryById(categories, value)

  const toggle = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleSelect = (node) => {
    onChange(String(node.id))
    if ((node.children ?? []).length > 0) {
      toggle(node.id)
    } else {
      setAnchorEl(null)
    }
  }

  const handleSelectAll = () => {
    onChange('')
    setAnchorEl(null)
  }

  return (
    <>
      <Box
        onClick={(e) => setAnchorEl(e.currentTarget)}
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 1,
          cursor: 'pointer',
          border: '1px solid',
          borderColor: open ? 'primary.main' : 'rgba(0,0,0,0.23)',
          borderRadius: 1,
          px: 1.5,
          height: size === 'small' ? 40 : 56,
          minWidth: fullWidth ? 0 : 160,
          width: fullWidth ? '100%' : 'auto',
          bgcolor: 'background.paper',
          '&:hover': { borderColor: 'text.primary' },
        }}
      >
        <Typography variant="body2" color={selected ? 'text.primary' : 'text.secondary'} noWrap>
          {selected ? selected.name : `${label} 전체`}
        </Typography>
        <ExpandMoreIcon
          fontSize="small"
          sx={{ color: 'action.active', transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
        />
      </Box>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
        slotProps={{ paper: { sx: { mt: 0.5, minWidth: 240, maxHeight: 360, overflowY: 'auto', py: 0.5 } } }}
      >
        <Box
          onClick={handleSelectAll}
          sx={{
            px: 1.5,
            py: 0.9,
            cursor: 'pointer',
            borderRadius: 1,
            fontWeight: !value ? 700 : 400,
            color: !value ? 'primary.main' : 'text.primary',
            '&:hover': { bgcolor: 'action.hover' },
          }}
        >
          <Typography variant="body2" fontWeight={!value ? 700 : 400} color={!value ? 'primary.main' : 'text.primary'}>
            전체
          </Typography>
        </Box>
        {categories.map((node) => (
          <CategoryOption
            key={node.id}
            node={node}
            depth={0}
            value={value}
            expandedIds={expandedIds}
            onToggle={toggle}
            onSelect={handleSelect}
          />
        ))}
      </Popover>
    </>
  )
}
