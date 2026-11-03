import SwiftUI
import Charts
import HerMorseCore

struct StatisticsView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        List {
            Section("Activité") {
                LabeledContent("Caractères manipulés",value: "\(model.stats.totalChars)")
                LabeledContent("Erreurs",value: "\(model.stats.errors)")
                LabeledContent("Séries terminées",value: "\(model.stats.series)")
                LabeledContent("Entraînement",value: "\(model.stats.trainingMs / 60000) min")
                LabeledContent("Appui cumulé",value: "\(model.stats.keyTimeMs / 1000) s")
                LabeledContent("Meilleure moyenne",value: String(format: "%.1f WPM",model.stats.bestWpm))
                LabeledContent("Jours d’affilée",value: "\(TrainingDays.streak(model.stats.days))")
                if !model.stats.days.isEmpty {
                    Chart(Array(model.stats.days.keys.sorted().suffix(14)),id: \.self) { day in
                        BarMark(x: .value("Jour",String(day.suffix(5))),y: .value("Minutes",Double(model.stats.days[day] ?? 0)/60000))
                    }.frame(height: 150)
                }
            }
            Section("Progression") {
                Text("Écoute · \(Levels.title(model.stats.rxLevel))")
                Text("Manipulation · \(Levels.title(model.stats.txLevel))")
                if !model.stats.history.isEmpty {
                    Chart(Array(model.stats.history.enumerated()),id: \.offset) { item in
                        LineMark(x: .value("Série",item.offset+1),y: .value("Réussite",item.element.a*100))
                            .foregroundStyle(by: .value("Piste",item.element.k == "rx" ? "Écoute" : "Manipulation"))
                    }.chartYScale(domain: 0...100).frame(height: 180)
                }
            }
            Section("Vitesse récente") {
                if model.stats.recentWpm.isEmpty { Text("Manipulez pour enregistrer votre vitesse.").foregroundStyle(.secondary) }
                else {
                    Chart(Array(model.stats.recentWpm.enumerated()),id: \.offset) { item in
                        LineMark(x: .value("Caractère",item.offset+1),y: .value("WPM",item.element))
                    }.frame(height: 140)
                }
                if !model.stats.recentRatio.isEmpty {
                    LabeledContent("Rapport trait / point",value: String(format: "%.2f (idéal : 3)",model.stats.recentRatio.reduce(0,+)/Double(model.stats.recentRatio.count)))
                }
            }
            characterSection("Réussite par caractère · écoute",table: model.stats.rxChars)
            characterSection("Réussite par caractère · manipulation",table: model.stats.txChars)
            Section("Confusions fréquentes") {
                if model.stats.confusions.isEmpty { Text("Aucune confusion enregistrée.").foregroundStyle(.secondary) }
                ForEach(model.stats.confusions.keys.sorted { model.stats.confusions[$0,default: 0] > model.stats.confusions[$1,default: 0] }.prefix(15).map { $0 },id: \.self) { key in
                    LabeledContent(key.replacingOccurrences(of: ">",with: " → "),value: "\(model.stats.confusions[key,default: 0])")
                }
            }
        }.navigationTitle("Statistiques")
    }
    private func characterSection(_ title: String, table: [String:[Int]]) -> some View {
        Section(title) {
            if table.isEmpty { Text("Terminez une série pour afficher ces résultats.").foregroundStyle(.secondary) }
            ForEach(table.keys.sorted(),id: \.self) { key in
                let pair = table[key] ?? [0,0]
                HStack {
                    Text(key).font(.system(.body,design: .monospaced)).frame(width: 45)
                    ProgressView(value: Double(pair[0]),total: Double(max(1,pair[1])))
                    Text("\(pair[0])/\(pair[1])").font(.caption)
                }
            }
        }
    }
}

struct TrophiesView: View {
    @ObservedObject var model: AppModel
    var body: some View {
        List(Trophies.all) { trophy in
            let earned = model.stats.trophies[trophy.id]
            HStack(alignment: .top,spacing: 14) {
                Image(systemName: earned == nil ? "lock.fill" : "trophy.fill").foregroundStyle(earned == nil ? .gray : .orange).font(.title2)
                VStack(alignment: .leading,spacing: 6) {
                    Text(trophy.tier == "SECRET" && earned == nil ? "Trophée secret" : trophy.name).bold()
                    if trophy.tier != "SECRET" || earned != nil { Text(trophy.detail).font(.caption).foregroundStyle(.secondary) }
                    if let earned { Text(Date(timeIntervalSince1970: Double(earned)/1000),style: .date).font(.caption) }
                    if !trophy.availableFrom.isEmpty { Text("Prévu dans Android \(trophy.availableFrom)").font(.caption).foregroundStyle(.secondary) }
                }
            }.padding(.vertical,6)
        }.navigationTitle("Trophées")
    }
}
