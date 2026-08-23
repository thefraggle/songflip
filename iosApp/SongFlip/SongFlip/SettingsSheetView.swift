import SwiftUI

struct SettingsSheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @Environment(\.dismiss) var dismiss
    @State private var showingLanguagePicker = false

    var currentLanguageItem: LanguageOption {
        SettingsModel.supportedLanguages.first { $0.code == settings.selectedLanguage }
            ?? SettingsModel.supportedLanguages[0]
    }

    var body: some View {
        NavigationStack {
            List {
                // 1. Language & Appearance Section
                Section(header: Text("ALLGEMEIN").font(.caption).fontWeight(.semibold)) {
                    Button(action: { showingLanguagePicker = true }) {
                        HStack {
                            Label("Sprache", systemImage: "globe")
                            Spacer()
                            Text("\(currentLanguageItem.flag) \(currentLanguageItem.name)")
                                .foregroundColor(.secondary)
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)

                    VStack(alignment: .leading, spacing: 8) {
                        Label("Erscheinungsbild", systemImage: "circle.righthalf.filled")
                        Picker("Theme", selection: $settings.themeMode) {
                            Text("Dunkel").tag("dark")
                            Text("Hell").tag("light")
                            Text("System").tag("system")
                        }
                        .pickerStyle(SegmentedPickerStyle())
                    }
                    .padding(.vertical, 4)

                    Toggle(isOn: $settings.autoClipboardDetect) {
                        Label("Zwischenablage automatisch scannen", systemImage: "doc.on.clipboard")
                    }
                    .tint(.green)
                }

                // 2. Active Input Platforms Section
                Section(header: Text("AKTIVE QUELL-DIENSTE").font(.caption).fontWeight(.semibold),
                        footer: Text("Bestimme, aus welchen Diensten Links verarbeitet werden sollen.").font(.caption2)) {
                    ForEach(PlatformChoice.allCases) { platform in
                        Toggle(isOn: Binding(
                            get: { settings.isInputPlatformEnabled(key: platform.rawValue) },
                            set: { settings.setInputPlatformEnabled(key: platform.rawValue, enabled: $0) }
                        )) {
                            Label(platform.displayName, systemImage: platform.iconName)
                        }
                        .tint(.green)
                    }
                }

                // 3. Custom API / Webhook Section
                Section(header: Text("ERWEITERT (CUSTOM API / KI)").font(.caption).fontWeight(.semibold)) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Webhook URL")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        TextField("https://...", text: $settings.customApiUrl)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Bearer Token (Optional)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        SecureField("Token...", text: $settings.customApiToken)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }
                }

                // 4. Legal & About Section
                Section(header: Text("ÜBER SONGFLIP").font(.caption).fontWeight(.semibold)) {
                    Link(destination: URL(string: "https://songflip.link")!) {
                        HStack {
                            Label("Website", systemImage: "safari")
                            Spacer()
                            Text("songflip.link")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }

                    Link(destination: URL(string: "https://songflip.link/privacy-policy.html")!) {
                        Label("Datenschutzerklärung", systemImage: "hand.raised")
                    }

                    Link(destination: URL(string: "https://songflip.link/imprint.html")!) {
                        Label("Impressum & Kontakt", systemImage: "info.circle")
                    }

                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.1.2 (iOS)")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Einstellungen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Fertig") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $showingLanguagePicker) {
                NavigationStack {
                    List(SettingsModel.supportedLanguages) { lang in
                        Button(action: {
                            settings.selectedLanguage = lang.code
                            showingLanguagePicker = false
                        }) {
                            HStack {
                                Text("\(lang.flag)  \(lang.name)")
                                    .foregroundColor(.primary)
                                Spacer()
                                if settings.selectedLanguage == lang.code {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.green)
                                        .fontWeight(.bold)
                                }
                            }
                        }
                    }
                    .navigationTitle("Sprache auswählen")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Schließen") { showingLanguagePicker = false }
                        }
                    }
                }
            }
        }
    }
}
