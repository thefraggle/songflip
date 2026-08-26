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
