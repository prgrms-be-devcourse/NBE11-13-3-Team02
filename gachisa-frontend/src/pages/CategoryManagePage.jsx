import { useCallback, useEffect, useState } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Collapse from '@mui/material/Collapse'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'
import AddIcon from '@mui/icons-material/Add'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import FolderRoundedIcon from '@mui/icons-material/FolderRounded'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import { getErrorMessage } from '../api/errorMessage'
import { getCategories, createCategory, updateCategory, deleteCategory } from '../api/categoryApi'
import LoadingScreen from '../components/LoadingScreen.jsx'

const INDENT = 28

function CategoryNode({ node, depth, isLast, onRefetch }) {
  const hasChildren = (node.children ?? []).length > 0
  const [expanded, setExpanded] = useState(true)

  const [addOpen, setAddOpen] = useState(false)
  const [newName, setNewName] = useState('')
  const [addSubmitting, setAddSubmitting] = useState(false)
  const [addError, setAddError] = useState('')

  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState(node.name)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editError, setEditError] = useState('')

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  const handleAddChild = async (e) => {
    e.preventDefault()
    setAddError('')
    setAddSubmitting(true)
    try {
      await createCategory({ name: newName, parentId: node.id })
      setNewName('')
      setAddOpen(false)
      setExpanded(true)
      onRefetch()
    } catch (err) {
      setAddError(getErrorMessage(err, '하위 카테고리 추가에 실패했습니다.'))
    } finally {
      setAddSubmitting(false)
    }
  }

  const handleUpdateName = async () => {
    setEditError('')
    setEditSubmitting(true)
    try {
      await updateCategory(node.id, { name: editName })
      setEditing(false)
      onRefetch()
    } catch (err) {
      setEditError(getErrorMessage(err, '이름 수정에 실패했습니다.'))
    } finally {
      setEditSubmitting(false)
    }
  }

  const handleDelete = async () => {
    setDeleteError('')
    setDeleteSubmitting(true)
    try {
      await deleteCategory(node.id)
      setDeleteOpen(false)
      onRefetch()
    } catch (err) {
      setDeleteError(getErrorMessage(err, '카테고리 삭제에 실패했습니다.'))
      setDeleteSubmitting(false)
    }
  }

  return (
    <Box sx={{ position: 'relative', pl: depth > 0 ? `${INDENT}px` : 0 }}>
      {/* 트리 가지선: 부모 화살표 중심(행의 px:1.5=12px + 22px 버튼의 절반=11px → 23px)에 맞춰
          세로선을 이어 그린다. 부모로부터 이어지는 세로선 + 이 노드로 꺾이는 가로선. */}
      {depth > 0 && (
        <>
          <Box
            sx={{
              position: 'absolute',
              left: 23,
              top: 0,
              // 마지막 형제 노드면 가로선이 꺾이는 지점(19px, 행 세로 중심)에서 선을 끊어 "└" 모양으로
              // 마무리하고, 아니면 다음 형제까지 이어지도록 끝까지("┣") 그린다.
              ...(isLast ? { height: 19 } : { bottom: 0 }),
              width: 0,
              borderLeft: '2px solid',
              borderColor: 'divider',
            }}
          />
          <Box
            sx={{
              position: 'absolute',
              left: 23,
              top: 19,
              width: 17,
              height: 0,
              borderTop: '2px solid',
              borderColor: 'divider',
            }}
          />
        </>
      )}

      <Box
        className="category-row"
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          py: 1,
          px: 1.5,
          borderRadius: 2,
          transition: 'background-color 0.15s',
          '&:hover': { bgcolor: 'action.hover' },
          '&:hover .category-actions': { opacity: 1, pointerEvents: 'auto' },
        }}
      >
        <IconButton
          size="small"
          onClick={() => setExpanded((v) => !v)}
          disabled={!hasChildren}
          sx={{
            width: 22,
            height: 22,
            flexShrink: 0,
            visibility: hasChildren ? 'visible' : 'hidden',
            transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)',
            transition: 'transform 0.15s',
          }}
        >
          <ChevronRightIcon fontSize="small" />
        </IconButton>

        <FolderRoundedIcon
          sx={{ fontSize: 20, color: depth === 0 ? 'primary.main' : 'text.disabled', flexShrink: 0 }}
        />

        {editing ? (
          <Stack direction="row" spacing={1} alignItems="center" sx={{ flexGrow: 1 }}>
            <TextField
              size="small"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              autoFocus
              sx={{ maxWidth: 240 }}
            />
            <IconButton size="small" color="primary" onClick={handleUpdateName} disabled={editSubmitting}>
              <CheckIcon fontSize="small" />
            </IconButton>
            <IconButton
              size="small"
              onClick={() => {
                setEditing(false)
                setEditName(node.name)
                setEditError('')
              }}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          </Stack>
        ) : (
          <>
            <Typography
              fontWeight={depth === 0 ? 700 : 500}
              sx={{ flexGrow: 1 }}
            >
              {node.name}
            </Typography>
            <Stack
              direction="row"
              spacing={0.5}
              className="category-actions"
              sx={{ opacity: 0, pointerEvents: 'none', transition: 'opacity 0.15s' }}
            >
              <Tooltip title="하위 카테고리 추가">
                <IconButton size="small" onClick={() => setAddOpen((v) => !v)}>
                  <AddIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="이름 수정">
                <IconButton size="small" onClick={() => setEditing(true)}>
                  <EditOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="삭제">
                <IconButton size="small" color="error" onClick={() => setDeleteOpen(true)}>
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          </>
        )}
      </Box>

      {editError && (
        <Alert severity="error" sx={{ ml: `${INDENT + 12}px`, mr: 1, mb: 1 }}>
          {editError}
        </Alert>
      )}

      <Collapse in={addOpen}>
        <Box
          component="form"
          onSubmit={handleAddChild}
          sx={{
            ml: `${INDENT + 12}px`,
            mr: 1,
            mb: 1,
            p: 1.5,
            borderRadius: 2,
            border: '1px dashed',
            borderColor: 'primary.light',
            bgcolor: 'primary.light',
            opacity: 0.97,
          }}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            <AddIcon fontSize="small" sx={{ color: 'primary.main' }} />
            <TextField
              size="small"
              placeholder="하위 카테고리 이름"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              required
              autoFocus
              sx={{ bgcolor: 'background.paper', borderRadius: 1, flexGrow: 1, maxWidth: 280 }}
            />
            <Button type="submit" size="small" variant="contained" disabled={addSubmitting}>
              추가
            </Button>
            <Button size="small" onClick={() => setAddOpen(false)}>
              취소
            </Button>
          </Stack>
          {addError && (
            <Alert severity="error" sx={{ mt: 1 }}>
              {addError}
            </Alert>
          )}
        </Box>
      </Collapse>

      <Collapse in={expanded}>
        {(node.children ?? []).map((child, index) => (
          <CategoryNode
            key={child.id}
            node={child}
            depth={depth + 1}
            isLast={index === node.children.length - 1}
            onRefetch={onRefetch}
          />
        ))}
      </Collapse>

      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
        <DialogTitle>카테고리 삭제</DialogTitle>
        <DialogContent>
          <DialogContentText>
            &apos;{node.name}&apos; 카테고리를 삭제하시겠습니까? 하위 카테고리나 등록된 상품이 있으면 삭제가 거부될
            수 있습니다.
          </DialogContentText>
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)}>취소</Button>
          <Button color="error" onClick={handleDelete} disabled={deleteSubmitting}>
            삭제
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

export default function CategoryManagePage() {
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [rootOpen, setRootOpen] = useState(false)
  const [rootName, setRootName] = useState('')
  const [rootSubmitting, setRootSubmitting] = useState(false)
  const [rootError, setRootError] = useState('')

  const fetchCategories = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const { data } = await getCategories()
      setCategories(data)
    } catch (err) {
      setError(getErrorMessage(err, '카테고리 목록을 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchCategories()
  }, [fetchCategories])

  const handleAddRoot = async (e) => {
    e.preventDefault()
    setRootError('')
    setRootSubmitting(true)
    try {
      await createCategory({ name: rootName, parentId: null })
      setRootName('')
      setRootOpen(false)
      fetchCategories()
    } catch (err) {
      setRootError(getErrorMessage(err, '카테고리 추가에 실패했습니다.'))
    } finally {
      setRootSubmitting(false)
    }
  }

  if (loading) return <LoadingScreen />

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2.5 }}>
        <Box>
          <Typography variant="h5" fontWeight={800}>
            카테고리 관리
          </Typography>
          <Typography variant="body2" color="text.secondary">
            항목에 마우스를 올리면 추가/수정/삭제 버튼이 나타납니다
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setRootOpen((v) => !v)}
          sx={{ whiteSpace: 'nowrap' }}
        >
          루트 카테고리 추가
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Collapse in={rootOpen}>
        <Paper
          component="form"
          onSubmit={handleAddRoot}
          variant="outlined"
          sx={{ p: 2, mb: 2, borderStyle: 'dashed', borderColor: 'primary.main', borderWidth: 1.5 }}
        >
          <Stack direction="row" spacing={2} alignItems="center">
            <TextField
              size="small"
              label="카테고리 이름"
              value={rootName}
              onChange={(e) => setRootName(e.target.value)}
              required
              autoFocus
              fullWidth
            />
            <Button type="submit" variant="contained" disabled={rootSubmitting} sx={{ whiteSpace: 'nowrap' }}>
              추가
            </Button>
          </Stack>
          {rootError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {rootError}
            </Alert>
          )}
        </Paper>
      </Collapse>

      <Paper variant="outlined" sx={{ p: 1.5 }}>
        {categories.length === 0 ? (
          <Typography color="text.secondary" sx={{ p: 2 }}>
            등록된 카테고리가 없습니다.
          </Typography>
        ) : (
          <Stack spacing={0.5}>
            {categories.map((node, index) => (
              <CategoryNode
                key={node.id}
                node={node}
                depth={0}
                isLast={index === categories.length - 1}
                onRefetch={fetchCategories}
              />
            ))}
          </Stack>
        )}
      </Paper>
    </Box>
  )
}
