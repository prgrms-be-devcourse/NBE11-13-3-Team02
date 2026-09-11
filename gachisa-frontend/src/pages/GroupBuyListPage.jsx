import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import Grid from '@mui/material/Grid'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import CardMedia from '@mui/material/CardMedia'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Chip from '@mui/material/Chip'
import LinearProgress from '@mui/material/LinearProgress'
import Alert from '@mui/material/Alert'
import Stack from '@mui/material/Stack'
import Pagination from '@mui/material/Pagination'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Collapse from '@mui/material/Collapse'
import CloseIcon from '@mui/icons-material/Close'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import StorefrontIcon from '@mui/icons-material/Storefront'
import { searchGroupBuys, cancelGroupBuy } from '../api/groupBuyApi'
import { getCategories } from '../api/categoryApi'
import { getErrorMessage } from '../api/errorMessage'
import { useAuth } from '../context/AuthContext.jsx'
import LoadingScreen from '../components/LoadingScreen.jsx'
import { GROUP_BUY_STATUS, formatPrice, statusMeta } from '../utils/statusMeta'
import { findCategoryById } from '../utils/categoryTree'

const PAGE_SIZE = 9
// 백엔드 GroupBuyService.applySort가 지원하는 값 그대로 사용한다 (그 외 값은 마감임박순으로 처리됨).
// "마감"은 참여 인원이 다 찼다는 뜻(참여 마감)인지 모집 기간이 끝난다는 뜻(모집 마감)인지 헷갈릴 수 있어서,
// 카드에 이미 쓰고 있는 "D-N" 표기와 맞춰 "며칠 남았는지" 기준이라는 걸 라벨에서 드러낸다.
const SORT_OPTIONS = [
  { value: 'deadline_asc', label: '마감일 임박순' },
  { value: 'price_asc', label: '낮은 가격순' },
  { value: 'price_desc', label: '높은 가격순' },
]

// 사이드바 카테고리 트리: 하위 카테고리가 있으면 화살표로 접었다 펼 수 있다.
function CategorySidebarNode({ node, activeCategoryId }) {
  const hasChildren = (node.children ?? []).length > 0
  const [expanded, setExpanded] = useState(true)
  const isActive = String(node.id) === activeCategoryId

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={0.2}>
        <IconButton
          size="small"
          onClick={() => setExpanded((v) => !v)}
          disabled={!hasChildren}
          sx={{
            width: 18,
            height: 18,
            flexShrink: 0,
            visibility: hasChildren ? 'visible' : 'hidden',
            transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)',
            transition: 'transform 0.15s',
          }}
        >
          <ChevronRightIcon sx={{ fontSize: 15 }} />
        </IconButton>
        <Typography
          component={Link}
          to={`/?categoryId=${node.id}`}
          variant="body2"
          fontWeight={isActive ? 700 : 400}
          color={isActive ? 'primary.main' : 'text.secondary'}
          sx={{ textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
        >
          {node.name}
        </Typography>
      </Stack>
      {hasChildren && (
        <Collapse in={expanded}>
          <Stack spacing={0.8} sx={{ pl: 2.2, mt: 0.8, borderLeft: '1px solid', borderColor: 'divider' }}>
            {node.children.map((child) => (
              <CategorySidebarNode key={child.id} node={child} activeCategoryId={activeCategoryId} />
            ))}
          </Stack>
        </Collapse>
      )}
    </Box>
  )
}

export default function GroupBuyListPage() {
  const { isAdmin } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const categoryId = searchParams.get('categoryId') ?? ''
  const keyword = searchParams.get('keyword') ?? ''
  const minPrice = searchParams.get('minPrice') ?? ''
  const maxPrice = searchParams.get('maxPrice') ?? ''
  const hasFilter = Boolean(categoryId || keyword || minPrice || maxPrice)

  const [page, setPage] = useState(0)
  // 정렬은 URL에 남기지 않는다 - 새로고침하면 다시 기본값(마감임박순)으로 초기화되는 게 의도된 동작이다.
  const [sort, setSort] = useState('deadline_asc')
  const [result, setResult] = useState({ content: [], totalPages: 0 })
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingId, setDeletingId] = useState(null)

  useEffect(() => {
    getCategories()
      .then(({ data }) => setCategories(data ?? []))
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    setPage(0)
  }, [categoryId, keyword, minPrice, maxPrice, sort])

  const fetchList = useCallback(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    searchGroupBuys({
      status: 'RECRUITING',
      keyword: keyword || undefined,
      categoryId: categoryId || undefined,
      minPrice: minPrice === '' ? undefined : Number(minPrice),
      maxPrice: maxPrice === '' ? undefined : Number(maxPrice),
      sort,
      page,
      size: PAGE_SIZE,
    })
      .then(({ data }) => {
        if (!cancelled) setResult(data)
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, '공동구매 목록을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryId, keyword, minPrice, maxPrice, sort, page])

  useEffect(() => fetchList(), [fetchList])

  const activeCategory = findCategoryById(categories, categoryId)
  const heading = keyword
    ? `'${keyword}' 검색 결과`
    : activeCategory
      ? `${activeCategory.name} 공동구매`
      : '진행중인 공동구매'

  const handleResetFilter = () => setSearchParams({})

  // 관리자 전용: 목록 카드에서 바로 공동구매 취소(운영 목적). 참여자가 있으면 자동 환불 흐름을 탄다.
  const handleAdminCancel = async (e, groupBuyId) => {
    e.preventDefault()
    e.stopPropagation()
    if (!window.confirm('이 공동구매를 취소하시겠습니까? 참여자가 있으면 환불이 진행됩니다.')) return
    setDeletingId(groupBuyId)
    try {
      await cancelGroupBuy(groupBuyId)
      fetchList()
    } catch (err) {
      setError(getErrorMessage(err, '공동구매 취소에 실패했습니다.'))
    } finally {
      setDeletingId(null)
    }
  }

  const content = result.content ?? []

  return (
    <Box sx={{ display: 'flex', gap: 4, alignItems: 'flex-start' }}>
      <Box sx={{ width: 180, flexShrink: 0, display: { xs: 'none', md: 'block' } }}>
        <Typography variant="overline" color="text.secondary">
          카테고리
        </Typography>
        <Stack spacing={1} sx={{ mt: 1, mb: 3 }}>
          <Typography
            component={Link}
            to="/"
            variant="body2"
            fontWeight={!categoryId ? 700 : 400}
            color={!categoryId ? 'primary.main' : 'text.secondary'}
            sx={{ textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
          >
            전체
          </Typography>
          {categories.map((c) => (
            <CategorySidebarNode key={c.id} node={c} activeCategoryId={categoryId} />
          ))}
        </Stack>

        <Typography variant="overline" color="text.secondary">
          정렬
        </Typography>
        <Stack spacing={1} sx={{ mt: 1 }}>
          {SORT_OPTIONS.map((opt) => (
            <Typography
              key={opt.value}
              variant="body2"
              fontWeight={sort === opt.value ? 700 : 400}
              color={sort === opt.value ? 'primary.main' : 'text.secondary'}
              onClick={() => setSort(opt.value)}
              sx={{ cursor: 'pointer' }}
            >
              {opt.label}
            </Typography>
          ))}
        </Stack>
      </Box>

      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h5" fontWeight={800}>
            {heading}
          </Typography>
          {hasFilter && (
            <Button size="small" onClick={handleResetFilter} sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>
              필터 초기화
            </Button>
          )}
        </Stack>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {loading ? (
          <LoadingScreen />
        ) : content.length === 0 ? (
          <Typography color="text.secondary">
            {hasFilter ? '조건에 맞는 진행중인 공동구매가 없습니다.' : '진행 중인 공동구매가 없습니다.'}
          </Typography>
        ) : (
          <>
            <Grid container spacing={2.5}>
              {content.map((gb) => {
                const meta = statusMeta(GROUP_BUY_STATUS, gb.status)
                const target = gb.targetCount ?? 0
                const current = gb.currentCount ?? 0
                const progress = target > 0 ? Math.min(100, (current / target) * 100) : 0

                const daysLeft = Math.ceil((new Date(gb.deadline) - Date.now()) / 86400000)
                const isRecruiting = gb.status === '모집중'
                const isFull = target > 0 && current >= target
                // 정산 배치가 아직 안 돌아서 백엔드 status가 '모집중'으로 남아있어도
                // 목표 인원에 도달했으면 프론트에서 선제적으로 "모집완료"로 보여준다.
                const displayLabel = isRecruiting && isFull ? '모집완료' : isRecruiting ? null : meta.label
                const displayColor = isRecruiting && isFull ? 'success' : meta.color

                return (
                  <Grid item xs={12} sm={6} lg={4} key={gb.groupBuyId}>
                    <Card
                      sx={{
                        position: 'relative',
                        ...(isFull ? { filter: 'grayscale(0.4)', opacity: 0.85 } : {}),
                      }}
                    >
                      {isAdmin && (
                        <IconButton
                          aria-label="공동구매 삭제"
                          onClick={(e) => handleAdminCancel(e, gb.groupBuyId)}
                          disabled={deletingId === gb.groupBuyId}
                          sx={{
                            position: 'absolute',
                            top: 6,
                            right: 6,
                            zIndex: 1,
                            bgcolor: 'rgba(0,0,0,0.55)',
                            color: '#fff',
                            '&:hover': { bgcolor: 'rgba(0,0,0,0.75)' },
                          }}
                          size="small"
                        >
                          <CloseIcon fontSize="small" />
                        </IconButton>
                      )}
                      <CardActionArea component={Link} to={`/group-buys/${gb.groupBuyId}`}>
                        <Box sx={{ position: 'relative', height: 110 }}>
                          {gb.imageUrl ? (
                            <CardMedia
                              component="img"
                              image={gb.imageUrl}
                              alt={gb.productName}
                              sx={{ height: 110, objectFit: 'cover' }}
                            />
                          ) : (
                            <Box
                              sx={{
                                height: 110,
                                bgcolor: 'primary.light',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                              }}
                            >
                              <StorefrontIcon sx={{ fontSize: 40, color: 'primary.main' }} />
                            </Box>
                          )}
                          {isFull && (
                            <Chip
                              size="small"
                              label="모집완료"
                              color="success"
                              sx={{ position: 'absolute', top: 8, left: 8, fontWeight: 700, color: '#fff' }}
                            />
                          )}
                        </Box>
                        <CardContent>
                          <Typography variant="subtitle1" fontWeight={700} noWrap gutterBottom>
                            {gb.productName}
                          </Typography>

                          <Stack direction="row" spacing={1} alignItems="baseline">
                            {gb.discountedPrice != null && (
                              <Typography variant="h6" fontWeight={800}>
                                {formatPrice(gb.discountedPrice)}
                              </Typography>
                            )}
                            {gb.basePrice != null && (
                              <Typography
                                variant="body2"
                                color="text.secondary"
                                sx={{ textDecoration: 'line-through' }}
                              >
                                {formatPrice(gb.basePrice)}
                              </Typography>
                            )}
                            {typeof gb.discountRate === 'number' && (
                              <Chip
                                size="small"
                                label={`${Math.round(gb.discountRate * 100)}%`}
                                sx={{ bgcolor: 'secondary.light', color: 'secondary.dark', fontWeight: 700 }}
                              />
                            )}
                          </Stack>

                          <Box sx={{ mt: 1.5 }}>
                            <LinearProgress
                              variant="determinate"
                              value={progress}
                              color={progress >= 100 ? 'success' : 'primary'}
                              sx={{ height: 6, borderRadius: 3 }}
                            />
                          </Box>

                          <Stack direction="row" justifyContent="space-between" sx={{ mt: 1 }}>
                            <Typography variant="caption" color="text.secondary">
                              {current}/{target}명 참여
                            </Typography>
                            <Typography variant="caption" fontWeight={700} color={`${displayColor}.main`}>
                              {displayLabel ?? (daysLeft > 0 ? `D-${daysLeft}` : '마감임박')}
                            </Typography>
                          </Stack>
                        </CardContent>
                      </CardActionArea>
                    </Card>
                  </Grid>
                )
              })}
            </Grid>

            {result.totalPages > 1 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
                <Pagination
                  page={page + 1}
                  count={result.totalPages}
                  onChange={(_, value) => setPage(value - 1)}
                  color="primary"
                />
              </Box>
            )}
          </>
        )}
      </Box>
    </Box>
  )
}
