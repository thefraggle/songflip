import SwiftUI
import SongFlipKit

enum FeedbackCategory: String, CaseIterable {
    case bug = "bug"
    case feature = "feature"
    case general = "general"

    func label(lang: String) -> String {
        switch self {
        case .bug: return LocalizationManager.string(for: "feedback_cat_bug", lang: lang)
        case .feature: return LocalizationManager.string(for: "feedback_cat_feature", lang: lang)
        case .general: return LocalizationManager.string(for: "feedback_cat_general", lang: lang)
        }
    }
}

struct FeedbackSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel

    @State private var selectedCategory: FeedbackCategory = .bug
    @State private var message: String = ""
    @State private var email: String = ""
    @State private var isSubmitting: Bool = false
    @State private var alertMessage: String? = nil
    @State private var showAlert: Bool = false

    var lang: String { settings.selectedLanguage }
    var isMessageValid: Bool { message.trimmingCharacters(in: .whitespacesAndNewlines).count >= 10 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Category Picker Chips
                    HStack(spacing: 8) {
                        ForEach(FeedbackCategory.allCases, id: \.self) { cat in
                            Button(action: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                selectedCategory = cat
                            }) {
                                Text(cat.label(lang: lang))
                                    .font(.subheadline)
                                    .fontWeight(selectedCategory == cat ? .bold : .medium)
                                    .padding(.vertical, 8)
                                    .frame(maxWidth: .infinity)
                                    .background(selectedCategory == cat ? Color.green.opacity(0.18) : Color(uiColor: .secondarySystemBackground))
                                    .foregroundColor(selectedCategory == cat ? .green : .primary)
                                    .cornerRadius(10)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(selectedCategory == cat ? Color.green : Color.clear, lineWidth: 1.5)
                                    )
                            }
                        }
                    }
                    .padding(.horizontal)

                    // Message Field
                    VStack(alignment: .leading, spacing: 6) {
                        ZStack(alignment: .topLeading) {
                            if message.isEmpty {
                                Text(LocalizationManager.string(for: "feedback_message_hint", lang: lang))
                                    .foregroundColor(.secondary.opacity(0.7))
                                    .font(.body)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                            }
                            TextEditor(text: $message)
                                .font(.body)
                                .frame(minHeight: 120)
                                .padding(8)
                                .scrollContentBackground(.hidden)
                                .background(Color(uiColor: .secondarySystemBackground))
                                .cornerRadius(12)
                        }

                        HStack {
                            if !message.isEmpty && !isMessageValid {
                                Text(LocalizationManager.string(for: "feedback_error_empty", lang: lang))
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                            Spacer()
                            Text("\(message.count) / 2000")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal)

                    // Optional Email
                    HStack {
                        Image(systemName: "envelope")
                            .foregroundColor(.secondary)
                        TextField(LocalizationManager.string(for: "feedback_email_hint", lang: lang), text: $email)
                            .font(.body)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                    .padding(12)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.horizontal)

                    // Zero Tracking Note
                    HStack(spacing: 8) {
                        Image(systemName: "lock.shield")
                            .foregroundColor(.green)
                            .font(.subheadline)
                        Text(LocalizationManager.string(for: "feedback_privacy_note", lang: lang))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground).opacity(0.6))
                    .cornerRadius(12)
                    .padding(.horizontal)

                    // Submit Button
                    Button(action: submitFeedback) {
                        ZStack {
                            if isSubmitting {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text(LocalizationManager.string(for: "feedback_btn_send", lang: lang))
                                    .font(.headline)
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(isMessageValid && !isSubmitting ? Color.green : Color.gray.opacity(0.4))
                        .cornerRadius(14)
                    }
                    .disabled(!isMessageValid || isSubmitting)
                    .padding(.horizontal)

                    // Email Fallback
                    Button(action: openMailFallback) {
                        Text(LocalizationManager.string(for: "feedback_btn_mail_fallback", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.green)
                    }
                    .disabled(isSubmitting)
                    .padding(.bottom, 16)
                }
                .padding(.top, 10)
            }
            .navigationTitle(LocalizationManager.string(for: "feedback_title", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LocalizationManager.string(for: "btn_close", lang: lang)) {
                        dismiss()
                    }
                }
            }
            .alert(isPresented: $showAlert) {
                Alert(
                    title: Text(alertMessage ?? ""),
                    dismissButton: .default(Text("OK")) {
                        if alertMessage == LocalizationManager.string(for: "feedback_success", lang: lang) {
                            dismiss()
                        }
                    }
                )
            }
            .preferredColorScheme(settings.colorScheme)
        }
    }

    private func submitFeedback() {
        guard isMessageValid, !isSubmitting else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isSubmitting = true

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.7.0"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        let osVersion = "iOS \(UIDevice.current.systemVersion)"
        let device = UIDevice.current.model

        let payload: [String: Any] = [
            "category": selectedCategory.rawValue,
            "message": message.trimmingCharacters(in: .whitespacesAndNewlines),
            "email": email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? NSNull() : email.trimmingCharacters(in: .whitespacesAndNewlines),
            "app_version": "v\(appVersion) (\(buildNumber))",
            "android_version": osVersion,
            "device": device
        ]

        guard let url = URL(string: "https://songflip-web.web.app/api/feedback") else {
            isSubmitting = false
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        Task {
            do {
                request.httpBody = try JSONSerialization.data(withJSONObject: payload)
                let (data, response) = try await URLSession.shared.data(for: request)
                isSubmitting = false

                if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                    alertMessage = LocalizationManager.string(for: "feedback_success", lang: lang)
                    showAlert = true
                } else {
                    alertMessage = LocalizationManager.string(for: "feedback_error_network", lang: lang)
                    showAlert = true
                }
            } catch {
                isSubmitting = false
                alertMessage = LocalizationManager.string(for: "feedback_error_network", lang: lang)
                showAlert = true
            }
        }
    }

    private func openMailFallback() {
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.7.0"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        let systemVersion = UIDevice.current.systemVersion
        let model = UIDevice.current.model
        let subject = "SongFlip iOS Feedback (v\(appVersion))".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "SongFlip%20Feedback"
        let body = "\n\n---\nApp Version: v\(appVersion) (\(buildNumber))\niOS: \(systemVersion)\nDevice: \(model)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let url = URL(string: "mailto:songflip@goork.de?subject=\(subject)&body=\(body)") {
            UIApplication.shared.open(url)
        }
        dismiss()
    }
}
