import { Navigate, Routes, Route } from 'react-router-dom'
import AppLayout from '../components/layout/AppLayout.jsx'
import ProtectedRoute from '../components/ProtectedRoute.jsx'
import RoleRoute from '../components/RoleRoute.jsx'

import LoginPage from '../pages/LoginPage.jsx'
import SignUpPage from '../pages/SignUpPage.jsx'
import SocialCallbackPage from '../pages/SocialCallbackPage.jsx'

import GroupBuyListPage from '../pages/GroupBuyListPage.jsx'
import GroupBuyDetailPage from '../pages/GroupBuyDetailPage.jsx'
import GroupBuyCreatePage from '../pages/GroupBuyCreatePage.jsx'
import GroupBuyCheckoutPage from '../pages/GroupBuyCheckoutPage.jsx'
import MyParticipationsPage from '../pages/MyParticipationsPage.jsx'
import ParticipationDetailPage from '../pages/ParticipationDetailPage.jsx'
import LegacyOrderRedirectPage from '../pages/LegacyOrderRedirectPage.jsx'
import DeliveryAddressPage from '../pages/DeliveryAddressPage.jsx'
import AdminDeliveryPage from '../pages/AdminDeliveryPage.jsx'
import PaymentSuccessPage from '../pages/PaymentSuccessPage.jsx'
import PaymentFailPage from '../pages/PaymentFailPage.jsx'
import RefundStatusPage from '../pages/RefundStatusPage.jsx'

import ProductListPage from '../pages/ProductListPage.jsx'
import ProductDetailPage from '../pages/ProductDetailPage.jsx'
import ProductCreatePage from '../pages/ProductCreatePage.jsx'
import ProductEditPage from '../pages/ProductEditPage.jsx'
import MyProductsPage from '../pages/MyProductsPage.jsx'
import CategoryManagePage from '../pages/CategoryManagePage.jsx'
import ConcurrencyDemoPage from '../pages/ConcurrencyDemoPage.jsx'

import MyPage from '../pages/MyPage.jsx'

import ForbiddenPage from '../pages/ForbiddenPage.jsx'
import NotFoundPage from '../pages/NotFoundPage.jsx'

export default function Router() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/oauth/:provider" element={<SocialCallbackPage />} />
      <Route path="/signup" element={<SignUpPage />} />
      <Route path="/payments/success" element={<PaymentSuccessPage />} />
      <Route path="/payments/fail" element={<PaymentFailPage />} />

      {/* AppLayout(네비바)은 로그인 여부와 무관하게 공통으로 쓴다. 상품/공동구매 둘러보기는
          비로그인 사용자도 가능해야 하므로 이 레벨에서는 ProtectedRoute를 두지 않는다. */}
      <Route element={<AppLayout />}>
        <Route path="/" element={<GroupBuyListPage />} />
        <Route path="/group-buys/:groupBuyId" element={<GroupBuyDetailPage />} />
        <Route path="/products" element={<ProductListPage />} />
        <Route path="/products/:productId" element={<ProductDetailPage />} />

        {/* 여기서부터는 실제 행동(참여, 주문, 등록 등)이라 로그인이 필요하다 */}
        <Route element={<ProtectedRoute />}>
          <Route path="/group-buys/:groupBuyId/checkout" element={<GroupBuyCheckoutPage />} />
          <Route path="/my/participations" element={<MyParticipationsPage />} />
          <Route path="/my/participations/:participationId" element={<ParticipationDetailPage />} />
          <Route path="/my/participations/:participationId/refund" element={<RefundStatusPage />} />
          <Route path="/my/orders" element={<Navigate to="/my/participations" replace />} />
          <Route path="/my/orders/:orderId" element={<LegacyOrderRedirectPage />} />

          <Route path="/my/page" element={<MyPage />} />
          <Route path="/orders/:orderId/delivery-address" element={<DeliveryAddressPage />} />

          <Route element={<RoleRoute roles={['ROLE_SELLER']} />}>
            <Route path="/group-buys/new" element={<GroupBuyCreatePage />} />
            <Route path="/products/new" element={<ProductCreatePage />} />
            <Route path="/products/:productId/edit" element={<ProductEditPage />} />
            <Route path="/my/products" element={<MyProductsPage />} />
          </Route>

          <Route element={<RoleRoute roles={['ROLE_SELLER', 'ROLE_ADMIN']} />}>
            <Route path="/dev/concurrency" element={<ConcurrencyDemoPage />} />
          </Route>

          <Route element={<RoleRoute roles={['ROLE_ADMIN']} />}>
            <Route path="/admin/categories" element={<CategoryManagePage />} />
            <Route path="/admin/deliveries" element={<AdminDeliveryPage />} />
          </Route>
        </Route>

        <Route path="/403" element={<ForbiddenPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
