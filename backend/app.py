"""
ArborTag Backend API
Flask server for tree data analysis
"""

from flask import Flask, request, send_file, jsonify
from flask_cors import CORS
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import os
import tempfile
from pathlib import Path

app = Flask(__name__)
CORS(app)

# Temporary directory for outputs
TEMP_DIR = tempfile.mkdtemp()

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "message": "ArborTag Analysis API is running",
        "version": "1.0.0"
    })

@app.route('/analyze/distribution', methods=['POST'])
def analyze_distribution():
    """Generate species distribution pie chart"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        # Count occurrences
        name_counts = df['scientific_name'].value_counts()

        # Create pie chart
        plt.figure(figsize=(12, 10))
        colors = plt.cm.tab20.colors
        plt.pie(name_counts, colors=colors, startangle=140)
        plt.title('Distribution of Trees by Scientific Name', fontsize=20)

        # Add legend with percentages
        percentages = [f'{p / sum(name_counts) * 100:.1f}%' for p in name_counts]
        legend_labels = [f'{name} - {percent}' for name, percent in zip(name_counts.index, percentages)]
        plt.legend(legend_labels, loc="center left", bbox_to_anchor=(1, 0.5), fontsize=12)

        # Save to file
        output_path = os.path.join(TEMP_DIR, 'distribution.png')
        plt.savefig(output_path, bbox_inches='tight', dpi=150)
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/height', methods=['POST'])
def analyze_height():
    """Generate height analysis bar chart"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        # Create bar chart
        plt.figure(figsize=(14, 10))
        plt.bar(df['scientific_name'], df['height'], color='maroon')
        plt.xlabel('Scientific Name', fontsize=14)
        plt.ylabel('Height (m)', fontsize=14)
        plt.title('Tree Heights by Species', fontsize=18)
        plt.xticks(rotation=90)
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'height.png')
        plt.savefig(output_path, dpi=150)
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/width', methods=['POST'])
def analyze_width():
    """Generate width analysis bar chart"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        # Calculate average width by species
        avg_widths = df.groupby('scientific_name')['width'].mean()

        plt.figure(figsize=(14, 10))
        plt.bar(avg_widths.index, avg_widths.values, color='green')
        plt.xlabel('Scientific Name', fontsize=14)
        plt.ylabel('Average Width (m)', fontsize=14)
        plt.title('Average Tree Width by Species', fontsize=18)
        plt.xticks(rotation=90)
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'width.png')
        plt.savefig(output_path, dpi=150)
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/stats', methods=['POST'])
def analyze_stats():
    """Generate statistical summary"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        # Calculate statistics
        stats = {
            "total_trees": len(df),
            "total_species": df['scientific_name'].nunique(),
            "avg_height": float(df['height'].mean()),
            "avg_width": float(df['width'].mean()),
            "total_carbon": float(df['carbon_seq'].sum()),
            "most_common_species": df['scientific_name'].mode()[0] if len(df) > 0 else "N/A",
            "species_distribution": df['scientific_name'].value_counts().to_dict()
        }

        return jsonify(stats)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/summary', methods=['POST'])
def analyze_summary():
    """Generate comprehensive summary"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        summary = {
            "project_stats": {
                "total_trees": len(df),
                "unique_species": df['scientific_name'].nunique(),
                "avg_height_m": round(df['height'].mean(), 2),
                "avg_width_m": round(df['width'].mean(), 2),
                "total_carbon_kg_year": round(df['carbon_seq'].sum(), 2)
            },
            "top_species": df['scientific_name'].value_counts().head(5).to_dict(),
            "height_range": {
                "min": float(df['height'].min()),
                "max": float(df['height'].max()),
                "median": float(df['height'].median())
            },
            "carbon_leaders": df.nlargest(5, 'carbon_seq')[['scientific_name', 'carbon_seq']].to_dict('records')
        }

        return jsonify(summary)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    print("=" * 50)
    print("ArborTag Backend API Starting...")
    print("=" * 50)
    print(f"Temporary directory: {TEMP_DIR}")
    print("Available endpoints:")
    print("  GET  /health")
    print("  POST /analyze/distribution")
    print("  POST /analyze/height")
    print("  POST /analyze/width")
    print("  POST /analyze/stats")
    print("  POST /analyze/summary")
    print("=" * 50)
    app.run(debug=True, host='0.0.0.0', port=5000)
