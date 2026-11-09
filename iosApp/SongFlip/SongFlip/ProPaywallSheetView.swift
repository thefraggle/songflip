import SwiftUI
import RevenueCat
import RevenueCatUI
import SongFlipKit

struct ProPaywallSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var proManager = ProManager.shared

    var offering: Offering? = nil

    var body: some View {
        Group {
            if let offering = offering {
                PaywallView(offering: offering)
            } else {
                PaywallView()
            }
        }
        .onPurchaseCompleted { customerInfo in
            proManager.updateFromCustomerInfo(customerInfo)
            dismiss()
        }
        .onRestoreCompleted { customerInfo in
            proManager.updateFromCustomerInfo(customerInfo)
            if customerInfo.entitlements["pro"]?.isActive == true ||
               customerInfo.entitlements["songflip_pro"]?.isActive == true {
                dismiss()
            }
        }
        .onDismiss {
            dismiss()
        }
        .task {
            if !proManager.isPro {
                proManager.trackPaywallImpression()
            }
        }
    }
}
