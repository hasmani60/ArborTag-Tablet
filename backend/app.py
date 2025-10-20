"""
ArborTag Backend API - Enhanced Version
Flask server for tree data analysis with additional visualizations
"""

from flask import Flask, request, send_file, jsonify
from flask_cors import CORS
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import numpy as np
import os
import tempfile
from pathlib import Path

app = Flask(__name__)
CORS(app)

# Temporary directory for outputs
TEMP_DIR = tempfile.mkdtemp()

def adjust_color_brightness_saturation(rgba_color, brightness_factor=0.8, saturation_factor=0.6):
    """Adjust color brightness and saturation for better visuals"""
    rgb_color = rgba_color[:3]
    hsv_color = mcolors.rgb_to_hsv(rgb_color)
    hsv_color[2] = np.clip(hsv_color[2] * brightness_factor, 0, 1)
    hsv_color[1] = np.clip(hsv_color[1] * saturation_factor, 0, 1)
    adjusted_rgb = mcolors.hsv_to_rgb(hsv_color)
    return (adjusted_rgb[0], adjusted_rgb[1], adjusted_rgb[2], rgba_color[3])

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "message": "ArborTag Analysis API is running",
        "version": "2.0.0"
    })

@app.route('/analyze/distribution', methods=['POST'])
def analyze_distribution():
    """Generate enhanced species distribution pie chart"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        name_counts = df['scientific_name'].value_counts()

        # Create combined color palette
        tab20 = plt.cm.get_cmap('tab20', 20)
        tab20b = plt.cm.get_cmap('tab20b', 20)
        tab20c = plt.cm.get_cmap('tab20c', 20)
        combined_colors = list(tab20.colors) + list(tab20b.colors) + list(tab20c.colors)
        combined_colors = [adjust_color_brightness_saturation(color) for color in combined_colors]

        num_categories = len(name_counts)
        if num_categories > len(combined_colors):
            combined_colors = combined_colors * (num_categories // len(combined_colors) + 1)
        combined_colors = combined_colors[:num_categories]

        plt.figure(figsize=(16, 12))
        patches, texts = plt.pie(name_counts, colors=combined_colors, startangle=140)
        plt.axis('equal')

        plt.title('Distribution of Trees by Scientific Name', fontsize=28, fontweight='bold', pad=20)

        percentages = [f'{p / sum(name_counts) * 100:.1f}%' for p in name_counts]
        legend_labels = [f'{name} - {percent}' for name, percent in zip(name_counts.index, percentages)]

        legend = plt.legend(patches, legend_labels, title="Scientific Names",
                          loc="center left", bbox_to_anchor=(1, 0.5),
                          fontsize=16, title_fontsize=18)

        plt.subplots_adjust(right=0.65)

        output_path = os.path.join(TEMP_DIR, 'distribution.png')
        plt.savefig(output_path, bbox_inches='tight', dpi=150, facecolor='white')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/height', methods=['POST'])
def analyze_height():
    """Generate height analysis with average line"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        plt.figure(figsize=(16, 10))

        # Calculate average height per species
        avg_heights = df.groupby('scientific_name')['height'].mean().sort_values(ascending=False)

        bars = plt.bar(range(len(avg_heights)), avg_heights.values,
                      color=plt.cm.viridis(np.linspace(0.3, 0.9, len(avg_heights))))

        # Add value labels on bars
        for i, (bar, value) in enumerate(zip(bars, avg_heights.values)):
            plt.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.3,
                    f'{value:.1f}m', ha='center', va='bottom', fontsize=10, fontweight='bold')

        plt.xlabel('Scientific Name', fontsize=16, fontweight='bold')
        plt.ylabel('Average Height (m)', fontsize=16, fontweight='bold')
        plt.title('Average Tree Heights by Species', fontsize=20, fontweight='bold', pad=20)
        plt.xticks(range(len(avg_heights)), avg_heights.index, rotation=45, ha='right')

        # Add grid for better readability
        plt.grid(axis='y', alpha=0.3, linestyle='--')

        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'height.png')
        plt.savefig(output_path, dpi=150, facecolor='white')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/width', methods=['POST'])
def analyze_width():
    """Generate width analysis with comparison"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        avg_widths = df.groupby('scientific_name')['width'].mean().sort_values(ascending=False)

        plt.figure(figsize=(16, 10))
        bars = plt.barh(range(len(avg_widths)), avg_widths.values,
                       color=plt.cm.Greens(np.linspace(0.4, 0.9, len(avg_widths))))

        # Add value labels
        for i, (bar, value) in enumerate(zip(bars, avg_widths.values)):
            plt.text(bar.get_width() + 0.02, bar.get_y() + bar.get_height()/2,
                    f'{value:.2f}m', va='center', fontsize=11, fontweight='bold')

        plt.ylabel('Scientific Name', fontsize=16, fontweight='bold')
        plt.xlabel('Average Width (m)', fontsize=16, fontweight='bold')
        plt.title('Average Tree Width by Species', fontsize=20, fontweight='bold', pad=20)
        plt.yticks(range(len(avg_widths)), avg_widths.index)

        plt.grid(axis='x', alpha=0.3, linestyle='--')
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'width.png')
        plt.savefig(output_path, dpi=150, facecolor='white')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/carbon_map', methods=['POST'])
def analyze_carbon_map():
    """Generate carbon sequestration scatter plot by location"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        plt.figure(figsize=(16, 12))

        # Create scatter plot with carbon sequestration as size and color
        scatter = plt.scatter(df['long'], df['lat'],
                            c=df['carbon_seq'],
                            s=df['carbon_seq']*10,  # Size based on carbon
                            cmap='RdYlGn',
                            alpha=0.6,
                            edgecolors='black',
                            linewidth=0.5)

        plt.colorbar(scatter, label='Carbon Sequestration (kg CO₂/year)', pad=0.02)

        plt.xlabel('Longitude', fontsize=16, fontweight='bold')
        plt.ylabel('Latitude', fontsize=16, fontweight='bold')
        plt.title('Geographic Distribution of Carbon Sequestration',
                 fontsize=20, fontweight='bold', pad=20)

        plt.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'carbon_map.png')
        plt.savefig(output_path, dpi=150, facecolor='white')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/canopy', methods=['POST'])
def analyze_canopy():
    """Generate canopy coverage analysis"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        # Filter out trees without canopy data
        df_canopy = df[df['canopy'].notna()]

        if len(df_canopy) == 0:
            return jsonify({"error": "No canopy data available"}), 400

        plt.figure(figsize=(16, 10))

        # Calculate average canopy per species
        avg_canopy = df_canopy.groupby('scientific_name')['canopy'].mean().sort_values(ascending=False)

        # Create bar chart
        bars = plt.bar(range(len(avg_canopy)), avg_canopy.values,
                      color=plt.cm.YlGn(np.linspace(0.4, 0.9, len(avg_canopy))))

        # Add value labels
        for i, (bar, value) in enumerate(zip(bars, avg_canopy.values)):
            plt.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                    f'{value:.2f}m', ha='center', va='bottom',
                    fontsize=10, fontweight='bold')

        plt.xlabel('Scientific Name', fontsize=16, fontweight='bold')
        plt.ylabel('Average Canopy Spread (m)', fontsize=16, fontweight='bold')
        plt.title('Average Canopy Coverage by Species',
                 fontsize=20, fontweight='bold', pad=20)
        plt.xticks(range(len(avg_canopy)), avg_canopy.index, rotation=45, ha='right')

        plt.grid(axis='y', alpha=0.3, linestyle='--')
        plt.tight_layout()

        output_path = os.path.join(TEMP_DIR, 'canopy.png')
        plt.savefig(output_path, dpi=150, facecolor='white')
        plt.close()

        return send_file(output_path, mimetype='image/png')

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/diversity', methods=['POST'])
def analyze_diversity():
    """Generate species diversity metrics"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

        species_counts = df['scientific_name'].value_counts()
        total_trees = len(df)

        # Calculate Shannon Diversity Index
        proportions = species_counts / total_trees
        shannon_index = -sum(proportions * np.log(proportions))

        # Calculate Simpson's Diversity Index
        simpson_index = 1 - sum(proportions ** 2)

        diversity_data = {
            "total_species": len(species_counts),
            "total_trees": total_trees,
            "shannon_diversity_index": round(float(shannon_index), 3),
            "simpson_diversity_index": round(float(simpson_index), 3),
            "most_abundant": species_counts.index[0],
            "most_abundant_count": int(species_counts.values[0]),
            "least_abundant": species_counts.index[-1],
            "least_abundant_count": int(species_counts.values[-1]),
            "species_distribution": species_counts.to_dict()
        }

        return jsonify(diversity_data)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/analyze/stats', methods=['POST'])
def analyze_stats():
    """Generate statistical summary"""
    try:
        file = request.files['file']
        df = pd.read_csv(file)

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
    print("=" * 60)
    print("ArborTag Backend API v2.0 Starting...")
    print("=" * 60)
    print(f"Temporary directory: {TEMP_DIR}")
    print("\nAvailable endpoints:")
    print("  GET  /health")
    print("  POST /analyze/distribution   - Species distribution pie chart")
    print("  POST /analyze/height         - Height analysis")
    print("  POST /analyze/width          - Width analysis")
    print("  POST /analyze/carbon_map     - Carbon sequestration map")
    print("  POST /analyze/canopy         - Canopy coverage analysis")
    print("  POST /analyze/diversity      - Species diversity metrics")
    print("  POST /analyze/stats          - Statistical summary")
    print("  POST /analyze/summary        - Comprehensive summary")
    print("=" * 60)
    app.run(debug=True, host='0.0.0.0', port=5000)