import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import MenuItem from '@mui/material/MenuItem'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import Select from '@mui/material/Select'
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate'
import { getErrorMessage } from '../api/errorMessage'
import { createProduct } from '../api/productApi'
import { getCategories } from '../api/categoryApi'

function flattenCategories(nodes, depth = 0) {
  return (nodes ?? []).flatMap((node) => [
    { id: node.id, label: `${'— '.repeat(depth)}${node.name}` },
    ...flattenCategories(node.children, depth + 1),
  ])
}

export default function ProductCreatePage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', description: '', basePrice: '', stock: '', categoryId: '' })
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const [categories, setCategories] = useState([])
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getCategories()
      .then(({ data }) => setCategories(flattenCategories(data)))
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    if (!imageFile) {
      setImagePreview(null)
      return undefined
    }
    const url = URL.createObjectURL(imageFile)
    setImagePreview(url)
    return () => URL.revokeObjectURL(url)
  }, [imageFile])

  const handleChange = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.categoryId) {
      setError('카테고리를 선택해주세요.')
      return
    }
    setSubmitting(true)
    try {
      const { data } = await createProduct({
        name: form.name,
        description: form.description,
        basePrice: Number(form.basePrice),
        stock: Number(form.stock),
        categoryId: form.categoryId,
        imageFile,
      })
      navigate(`/products/${data.id}`)
    } catch (err) {
      setError(getErrorMessage(err, '상품 등록에 실패했습니다.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box sx={{ maxWidth: 700, mx: 'auto', p: 3 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" fontWeight={700} gutterBottom>
          상품 등록
        </Typography>
        <Box component="form" onSubmit={handleSubmit} sx={{ mt: 2 }}>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}

            <Box>
              <Typography variant="body2" fontWeight={700} sx={{ mb: 1 }}>
                상품 이미지
              </Typography>
              <Button
                component="label"
                variant="outlined"
                startIcon={<AddPhotoAlternateIcon />}
                sx={{ display: 'flex', width: '100%', height: 160, borderStyle: 'dashed' }}
              >
                {imagePreview ? (
                  <Box
                    component="img"
                    src={imagePreview}
                    alt="미리보기"
                    sx={{ maxHeight: 150, maxWidth: '100%', objectFit: 'contain' }}
                  />
                ) : (
                  '이미지 파일 선택 (jpg, png, gif, webp)'
                )}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  hidden
                  onChange={(e) => setImageFile(e.target.files?.[0] ?? null)}
                />
              </Button>
            </Box>

            <TextField label="상품명" value={form.name} onChange={handleChange('name')} required fullWidth />
            <TextField
              label="설명"
              value={form.description}
              onChange={handleChange('description')}
              required
              fullWidth
              multiline
              minRows={3}
            />
            <TextField
              label="기본 가격"
              type="number"
              value={form.basePrice}
              onChange={handleChange('basePrice')}
              required
              fullWidth
            />
            <TextField
              label="재고"
              type="number"
              value={form.stock}
              onChange={handleChange('stock')}
              inputProps={{ min: 0 }}
              required
              fullWidth
            />
            <FormControl fullWidth required>
              <InputLabel id="category-select-label">카테고리</InputLabel>
              <Select
                labelId="category-select-label"
                label="카테고리"
                value={form.categoryId}
                onChange={handleChange('categoryId')}
              >
                {categories.map((cat) => (
                  <MenuItem key={cat.id} value={cat.id}>
                    {cat.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Button type="submit" variant="contained" size="large" disabled={submitting}>
              {submitting ? '등록 중...' : '상품 등록'}
            </Button>
          </Stack>
        </Box>
      </Paper>
    </Box>
  )
}
